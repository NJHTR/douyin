package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.common.PageDTO;
import com.douyin.common.CursorPageDTO;
import com.douyin.entity.Follow;
import com.douyin.entity.Like;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.entity.VideoCollect;
import com.douyin.entity.VideoContent;
import com.douyin.entity.WatchHistory;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.FeedChannel;
import com.douyin.service.RecommendationConfig;
import com.douyin.service.RecommendationEngine;
import com.douyin.service.RedisCacheService;
import com.douyin.service.SearchService;
import com.douyin.service.VideoService;
import com.douyin.vo.UserVO;
import com.douyin.vo.VideoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    private final UserMapper userMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final VideoCollectMapper collectMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final RecommendationEngine recommendationEngine;
    private final ContentFeatureService contentFeatureService;
    private final VideoContentMapper videoContentMapper;
    private final SearchService searchService;
    private final RedisCacheService redisCacheService;
    private static final long LOCAL_RECOMMEND_POOL_TTL_MS = 5 * 60_000L;
    /** Hard bound for the per-JVM acceleration cache; Redis remains the shared source. */
    private static final int MAX_LOCAL_RECOMMEND_POOLS = 1000;
    private final LinkedHashMap<String, LocalRecommendationPool> localRecommendationPools =
            new LinkedHashMap<>();

    private static final Set<String> FILM_TV_CATEGORIES = Set.of(
            "影视", "综艺", "电影", "电视剧", "纪录片", "动漫", "娱乐");

    public VideoServiceImpl(UserMapper userMapper, LikeMapper likeMapper, FollowMapper followMapper,
                            VideoCollectMapper collectMapper, WatchHistoryMapper watchHistoryMapper,
                            RecommendationEngine recommendationEngine,
                            ContentFeatureService contentFeatureService,
                            VideoContentMapper videoContentMapper,
                            SearchService searchService) {
        this(userMapper, likeMapper, followMapper, collectMapper, watchHistoryMapper,
                recommendationEngine, contentFeatureService, videoContentMapper, searchService, null);
    }

    /** Spring constructor; the shorter constructor remains for isolated unit tests. */
    @Autowired
    public VideoServiceImpl(UserMapper userMapper, LikeMapper likeMapper, FollowMapper followMapper,
                            VideoCollectMapper collectMapper, WatchHistoryMapper watchHistoryMapper,
                            RecommendationEngine recommendationEngine,
                            ContentFeatureService contentFeatureService,
                            VideoContentMapper videoContentMapper,
                            SearchService searchService, RedisCacheService redisCacheService) {
        this.userMapper = userMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.collectMapper = collectMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.recommendationEngine = recommendationEngine;
        this.contentFeatureService = contentFeatureService;
        this.videoContentMapper = videoContentMapper;
        this.searchService = searchService;
        this.redisCacheService = redisCacheService;
    }

    @Override
    public PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize, String type) {
        return getRecommended(viewerUserId, start, pageSize, type, FeedChannel.HOME);
    }

    @Override
    public PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize,
                                           String type, FeedChannel channel) {
        return getRecommended(viewerUserId, start, pageSize, type, channel, null);
    }

    @Override
    public PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize,
                                           String type, FeedChannel channel, String clientSessionId) {
        int safeStart = Math.max(0, start);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        FeedChannel effectiveChannel = channel == null ? FeedChannel.HOME : channel;
        // LONG_VIDEO is a channel contract, so callers using the generic
        // recommendation endpoint must receive the same duration filtering
        // as the dedicated /video/long/recommended route.
        Double minDuration = "long-video".equals(type) || effectiveChannel == FeedChannel.LONG_VIDEO
                ? 60.0 : null;

        if (effectiveChannel == FeedChannel.FOLLOWING) {
            return getFollowingVideos(viewerUserId, safeStart / safePageSize + 1, safePageSize);
        }

        // Explicit channels never silently fall through to the generic
        // latest-videos query.  Each request asks the engine for one bounded
        // page; exposure exclusion advances the user's feed for the next page.
        if ((viewerUserId != null && (effectiveChannel.isPersonalized()
                || effectiveChannel == FeedChannel.FRIENDS || effectiveChannel.isGlobal()))
                || effectiveChannel == FeedChannel.HOT) {
            // Build a bounded pool, not just the currently requested page. A
            // page-sized pool would make the next request (start > 0) observe
            // a cached list that is too short and return an empty page.
            int poolSize = RecommendationConfig.CANDIDATE_POOL_SIZE;
            List<Long> rankedIds = getOrBuildRecommendationPool(viewerUserId, effectiveChannel,
                    clientSessionId, minDuration, poolSize);
            if (!rankedIds.isEmpty()) {
                List<Video> videos = listByIds(rankedIds);
                videos = videos.stream()
                        .filter(v -> "APPROVED".equals(v.getStatus()))
                        .filter(v -> List.of("recommend-video", "image", "text").contains(v.getType()))
                        .filter(v -> minDuration == null
                                || (v.getDuration() != null && v.getDuration() >= minDuration))
                        .toList();
                // 恢复排序
                Map<Long, Video> videoMap = videos.stream()
                        .collect(Collectors.toMap(Video::getId, v -> v));
                List<Video> ordered = rankedIds.stream()
                        .map(videoMap::get).filter(Objects::nonNull).toList();
                int from = Math.min(safeStart, ordered.size());
                int to = Math.min(from + safePageSize, ordered.size());
                List<Video> slice = from < to ? ordered.subList(from, to) : List.of();
                List<VideoVO> voList = toVideoVOList(slice, viewerUserId);
                if (!slice.isEmpty()) {
                    recommendationEngine.recordExposures(viewerUserId,
                            slice.stream().map(Video::getId).toList());
                }
                return new PageDTO<>((long) rankedIds.size(), safeStart / safePageSize + 1,
                        safePageSize, voList);
            }
        }

        if (effectiveChannel == FeedChannel.FRIENDS || effectiveChannel == FeedChannel.LIVE) {
            return new PageDTO<>(0L, safeStart / safePageSize + 1, safePageSize, List.of());
        }

        // Personalized failure degradation: use the global hot index before
        // falling back to chronological newest content.
        if (effectiveChannel.isPersonalized()) {
            PageDTO<VideoVO> hotFallback = getHotFallback(viewerUserId, safeStart,
                    safePageSize, minDuration);
            if (!hotFallback.getList().isEmpty()) return hotFallback;
        }

        // FOLLOWING/Friends/Live/anonymous/engine-empty fallback.  Keep the
        // fallback bounded and mode-aware instead of returning an unbounded
        // newest slice for every channel.
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        int pageNo = safeStart / safePageSize + 1;
        IPage<Video> page = page(new Page<>(pageNo, safePageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, safePageSize, voList);
    }

    private PageDTO<VideoVO> getHotFallback(Long viewerUserId, int start, int pageSize,
                                            Double minDuration) {
        LambdaQueryWrapper<Video> hotWrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .ge(Video::getCreateTime, LocalDateTime.now().minusDays(7))
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT 200");
        if (minDuration != null) hotWrapper.ge(Video::getDuration, minDuration);
        List<Video> candidates = new ArrayList<>(list(hotWrapper));
        long now = System.currentTimeMillis();
        candidates.sort(Comparator
                .comparingDouble((Video video) -> hotScore(video, now)).reversed()
                .thenComparing(Video::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        int from = Math.min(start, candidates.size());
        int to = Math.min(from + pageSize, candidates.size());
        List<Video> slice = from < to ? candidates.subList(from, to) : List.of();
        return new PageDTO<>((long) candidates.size(), start / pageSize + 1,
                pageSize, toVideoVOList(slice, viewerUserId));
    }

    private List<Long> getOrBuildRecommendationPool(Long userId, FeedChannel channel,
                                                     String clientSessionId, Double minDuration,
                                                     int poolSize) {
        String localKey = userId + ":" + channel.name() + ":" +
                (clientSessionId == null ? "legacy" : clientSessionId);
        long now = System.currentTimeMillis();
        LocalRecommendationPool local = getLocalRecommendationPool(localKey, now);
        if (local != null && !local.videoIds().isEmpty()) {
            return local.videoIds();
        }
        if (redisCacheService != null) {
            Optional<List<Long>> cached = redisCacheService.getRecommendPool(userId, channel, clientSessionId);
            if (cached.isPresent() && !cached.get().isEmpty()) {
                cacheLocalRecommendationPool(localKey, now, cached.get());
                return cached.get();
            }
        }
        try {
            List<Long> built = recommendationEngine.recommendPool(userId, poolSize,
                    minDuration, channel);
            if (redisCacheService != null && !built.isEmpty()) {
                redisCacheService.putRecommendPool(userId, channel, clientSessionId, built);
            }
            if (!built.isEmpty()) {
                cacheLocalRecommendationPool(localKey, now, built);
            }
            return built;
        } catch (RuntimeException e) {
            log.warn("recommendation pool failed: userId={} channel={} error={}",
                    userId, channel, e.getMessage());
            return List.of();
        }
    }

    /**
     * Keep the local cache bounded even when all sessions are active. The
     * cache is only an optimization: an eviction can always be repopulated
     * from the shared Redis pool (or rebuilt by the recommendation engine).
     */
    private synchronized LocalRecommendationPool getLocalRecommendationPool(String key, long now) {
        LocalRecommendationPool local = localRecommendationPools.get(key);
        if (local != null && now - local.createdAt() >= LOCAL_RECOMMEND_POOL_TTL_MS) {
            localRecommendationPools.remove(key);
            return null;
        }
        return local;
    }

    private synchronized void cacheLocalRecommendationPool(String key, long now, List<Long> videoIds) {
        // Reinsert an existing key so insertion order continues to match the
        // pool's latest creation time.
        localRecommendationPools.remove(key);
        localRecommendationPools.put(key, new LocalRecommendationPool(now, videoIds));
        localRecommendationPools.entrySet().removeIf(entry ->
                now - entry.getValue().createdAt() >= LOCAL_RECOMMEND_POOL_TTL_MS);
        while (localRecommendationPools.size() > MAX_LOCAL_RECOMMEND_POOLS) {
            Iterator<Map.Entry<String, LocalRecommendationPool>> iterator =
                    localRecommendationPools.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private record LocalRecommendationPool(long createdAt, List<Long> videoIds) {}

    @Override
    public PageDTO<VideoVO> getFollowingVideos(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId == null) return new PageDTO<>(0, pageNo, pageSize, List.of());
        // 查询关注用户ID列表
        List<Long> followedIds = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, viewerUserId))
                .stream().map(Follow::getFollowId).toList();
        if (followedIds.isEmpty()) return new PageDTO<>(0, pageNo, pageSize, List.of());

        // 首页: 从关注作者中取候选，按质量+热度综合排序
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, followedIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT 200");
        List<Video> candidates = new ArrayList<>(list(wrapper));
        // 综合分 = 时间衰减 + 互动量加成
        long now = System.currentTimeMillis();
        candidates.sort(Comparator
                .comparingDouble((Video video) -> followingScore(video, now)).reversed()
                .thenComparing(Video::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, candidates.size());
        List<VideoVO> voList = toVideoVOList(
                start < candidates.size() ? candidates.subList(start, end) : List.of(), viewerUserId);
        return new PageDTO<>((long) candidates.size(), pageNo, pageSize, voList);
    }

    /** 关注页综合分: 时间衰减 (72h 半衰期) × (1 + log互动量) */
    private double followingScore(Video v, long nowMs) {
        double likes = (v.getLikeCount() != null ? v.getLikeCount() : 0)
                + (v.getShareCount() != null ? v.getShareCount() : 0)
                + (v.getCollectCount() != null ? v.getCollectCount() : 0);
        double hoursAge = (nowMs - v.getCreateTime().atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()) / 3600_000.0;
        double recency = 1.0 / (1.0 + hoursAge / 72.0);
        double engagement = 1.0 + Math.log10(1.0 + likes);
        return recency * engagement;
    }

    @Override
    public PageDTO<VideoVO> getTrendingVideos(Long viewerUserId, int pageNo, int pageSize) {
        // 兜底: 热度分排序 (like_count + recency 衰减)
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .ge(Video::getCreateTime, java.time.LocalDateTime.now().minusDays(7))
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT 200");
        List<Video> candidates = new ArrayList<>(list(wrapper));
        // 热度分 = like_count × 时间衰减 (48h 半衰期)
        long now = System.currentTimeMillis();
        candidates.sort(Comparator
                .comparingDouble((Video video) -> hotScore(video, now)).reversed()
                .thenComparing(Video::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, candidates.size());
        List<VideoVO> voList = toVideoVOList(
                start < candidates.size() ? candidates.subList(start, end) : List.of(), viewerUserId);
        return new PageDTO<>((long) candidates.size(), pageNo, pageSize, voList);
    }

    /** 热度分: like_count × 时间衰减因子 (半衰期 48h) */
    private double hotScore(Video v, long nowMs) {
        double likes = v.getLikeCount() != null ? v.getLikeCount() : 0;
        double hoursAge = (nowMs - v.getCreateTime().atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()) / 3600_000.0;
        double decay = 1.0 / (1.0 + hoursAge / 48.0);
        return likes * decay;
    }

    @Override
    public PageDTO<VideoVO> getUserVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getAuthorUserId, userId)
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getMyVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getAuthorUserId, userId)
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getLikedVideos(Long userId, int pageNo, int pageSize) {
        // 从 t_like 查出该用户点赞的所有视频ID
        LambdaQueryWrapper<Like> likeWrapper = new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .orderByDesc(Like::getCreateTime);
        IPage<Like> likePage = likeMapper.selectPage(new Page<>(pageNo, pageSize), likeWrapper);
        List<Long> videoIds = likePage.getRecords().stream()
                .map(Like::getVideoId).toList();

        if (videoIds.isEmpty()) return new PageDTO<>(likePage.getTotal(), pageNo, pageSize, List.of());

        List<Video> videos = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        // 按点赞顺序排列
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a, LinkedHashMap::new));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get).filter(Objects::nonNull).toList();

        List<VideoVO> voList = toVideoVOList(ordered, userId);
        return new PageDTO<>(likePage.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getPrivateVideos(Long viewerUserId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getType, "private");
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getHistory(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId == null) return new PageDTO<>(0, pageNo, pageSize, List.of());
        int offset = (pageNo - 1) * pageSize;
        List<Long> videoIds = watchHistoryMapper.findHistoryVideoIds(viewerUserId, offset, pageSize);
        if (videoIds.isEmpty()) return new PageDTO<>(0, pageNo, pageSize, List.of());
        List<Video> videos = baseMapper.selectBatchIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        // 恢复原始顺序
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
        List<VideoVO> voList = toVideoVOList(ordered, viewerUserId);
        return new PageDTO<>((long) videoIds.size(), pageNo, pageSize, voList);
    }

    @Override
    public CursorPageDTO<VideoVO> getHistoryCursor(Long viewerUserId, String cursor, int pageSize) {
        if (viewerUserId == null) return new CursorPageDTO<>(List.of(), null, false);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        CursorPosition position = decodeHistoryCursor(cursor);
        List<WatchHistory> rows = watchHistoryMapper.findHistoryCursor(
                viewerUserId,
                position == null ? null : position.updateTime(),
                position == null ? null : position.id(),
                safeSize + 1);
        boolean hasMore = rows.size() > safeSize;
        List<WatchHistory> pageRows = hasMore ? rows.subList(0, safeSize) : rows;
        if (pageRows.isEmpty()) return new CursorPageDTO<>(List.of(), null, false);

        List<Long> videoIds = pageRows.stream().map(WatchHistory::getVideoId).toList();
        Map<Long, Video> videoMap = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus()))
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a));
        List<Video> ordered = videoIds.stream().map(videoMap::get).filter(Objects::nonNull).toList();
        String nextCursor = hasMore
                ? encodeHistoryCursor(pageRows.get(pageRows.size() - 1))
                : null;
        return new CursorPageDTO<>(toVideoVOList(ordered, viewerUserId), nextCursor, hasMore);
    }

    private static String encodeHistoryCursor(WatchHistory row) {
        if (row.getUpdateTime() == null || row.getId() == null) return null;
        String raw = row.getUpdateTime().toString() + "|" + row.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeHistoryCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator <= 0 || separator == raw.length() - 1) throw new IllegalArgumentException();
            LocalDateTime updateTime = LocalDateTime.parse(raw.substring(0, separator));
            long id = Long.parseLong(raw.substring(separator + 1));
            if (id <= 0) throw new IllegalArgumentException();
            return new CursorPosition(updateTime, id);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("观看历史游标无效");
        }
    }

    private record CursorPosition(LocalDateTime updateTime, Long id) {}

    @Override
    public PageDTO<VideoVO> getHistoryOther(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId == null) return new PageDTO<>(0, pageNo, pageSize, List.of());

        // 获取用户所有观看历史 (取较多数量用于筛选)
        int offset = (pageNo - 1) * pageSize;
        List<Long> allVideoIds = watchHistoryMapper.findHistoryVideoIds(
                viewerUserId, 0, (pageNo + 2) * pageSize);

        if (allVideoIds.isEmpty()) return new PageDTO<>(0, pageNo, pageSize, List.of());

        // 筛选出 "影视综" 分类的视频
        List<VideoContent> contents = videoContentMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .in(VideoContent::getVideoId, allVideoIds)
                        .in(VideoContent::getTextCategory, FILM_TV_CATEGORIES));
        Set<Long> filmTvVideoIds = contents.stream()
                .map(VideoContent::getVideoId).collect(Collectors.toSet());

        // 保持观看顺序，仅保留影视综
        List<Long> filteredIds = allVideoIds.stream()
                .filter(filmTvVideoIds::contains).toList();
        long total = filteredIds.size();

        List<Long> pageIds = filteredIds.stream()
                .skip(offset).limit(pageSize).toList();

        if (pageIds.isEmpty()) return new PageDTO<>(total, pageNo, pageSize, List.of());

        Map<Long, Video> videoMap = listByIds(pageIds).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));
        List<Video> ordered = pageIds.stream()
                .map(videoMap::get).filter(Objects::nonNull).toList();

        return new PageDTO<>(total, pageNo, pageSize, toVideoVOList(ordered, viewerUserId));
    }

    @Override
    public CursorPageDTO<VideoVO> getHistoryOtherCursor(Long viewerUserId, String cursor, int pageSize) {
        if (viewerUserId == null) return new CursorPageDTO<>(List.of(), null, false);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        CursorPosition position = decodeHistoryCursor(cursor);
        List<WatchHistory> rows = watchHistoryMapper.findHistoryOtherCursor(
                viewerUserId,
                FILM_TV_CATEGORIES,
                position == null ? null : position.updateTime(),
                position == null ? null : position.id(),
                safeSize + 1);
        boolean hasMore = rows.size() > safeSize;
        List<WatchHistory> pageRows = hasMore ? rows.subList(0, safeSize) : rows;
        if (pageRows.isEmpty()) return new CursorPageDTO<>(List.of(), null, false);

        List<Long> videoIds = pageRows.stream().map(WatchHistory::getVideoId).toList();
        Map<Long, Video> videoMap = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus()))
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a));
        List<Video> ordered = videoIds.stream().map(videoMap::get).filter(Objects::nonNull).toList();
        String nextCursor = hasMore
                ? encodeHistoryCursor(pageRows.get(pageRows.size() - 1))
                : null;
        return new CursorPageDTO<>(toVideoVOList(ordered, viewerUserId), nextCursor, hasMore);
    }

    @Override
    public PageDTO<VideoVO> getRecommendedPosts(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId != null && pageNo == 1) {
            // 首页：走推荐引擎，然后仅保留 image/text 类型
            List<Long> rankedIds = recommendationEngine.recommend(viewerUserId,
                    pageSize * 4, null);
            if (!rankedIds.isEmpty()) {
                List<Video> videos = listByIds(rankedIds);
                videos = videos.stream()
                        .filter(v -> "APPROVED".equals(v.getStatus()))
                        .filter(v -> v.getDuration() != null)
                        .toList();
                Map<Long, Video> videoMap = videos.stream()
                        .collect(Collectors.toMap(Video::getId, v -> v));
                List<Video> ordered = rankedIds.stream()
                        .map(videoMap::get)
                        .filter(Objects::nonNull)
                        .filter(v -> "image".equals(v.getType()) || "text".equals(v.getType()))
                        .limit(pageSize).toList();
                if (!ordered.isEmpty()) {
                    List<VideoVO> voList = toVideoVOList(ordered, viewerUserId);
                    return new PageDTO<>((long) rankedIds.size(), 1, pageSize, voList);
                }
            }
        }

        // 兜底：简单时间排序
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getRecommendedGoods(int pageNo, int pageSize) {
        // 暂无独立商品系统，返回热门视频作为推荐内容
        Page<Video> page = page(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, "APPROVED")
                        .orderByDesc(Video::getLikeCount)
                        .orderByDesc(Video::getId));
        return new PageDTO<>(page.getTotal(), pageNo, pageSize,
                toVideoVOList(page.getRecords(), null));
    }

    /** 将 Video 列表转换为 VideoVO 列表, 嵌入 author 信息, 批量查询当前用户的点赞状态 */
    private List<VideoVO> toVideoVOList(List<Video> videos, Long viewerUserId) {
        if (videos.isEmpty()) return List.of();

        // 批量查询作者信息
        List<Long> authorIds = videos.stream()
                .map(Video::getAuthorUserId).distinct().toList();
        List<User> users = userMapper.selectBatchIds(authorIds);
        Map<Long, UserVO> userMap = users.stream()
                .collect(Collectors.toMap(User::getUid, UserVO::from));

        // 批量查询当前用户的点赞状态
        Set<Long> likedVideoIds = Set.of();
        if (viewerUserId != null) {
            List<Long> videoIds = videos.stream().map(Video::getId).toList();
            likedVideoIds = likeMapper.selectList(new LambdaQueryWrapper<Like>()
                            .eq(Like::getUserId, viewerUserId)
                            .in(Like::getVideoId, videoIds))
                    .stream().map(Like::getVideoId)
                    .collect(Collectors.toSet());
        }

        // 批量查询当前用户是否关注了这些作者
        Set<Long> finalLiked = likedVideoIds;
        Set<Long> followedAuthorIds = Set.of();
        if (viewerUserId != null && !authorIds.isEmpty()) {
            followedAuthorIds = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, viewerUserId)
                            .in(Follow::getFollowId, authorIds))
                    .stream().map(Follow::getFollowId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalFollowed = followedAuthorIds;

        // 批量查询当前用户的收藏状态
        Set<Long> collectedVideoIds = Set.of();
        if (viewerUserId != null) {
            List<Long> videoIds = videos.stream().map(Video::getId).toList();
            collectedVideoIds = collectMapper.selectList(new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUserId, viewerUserId)
                            .in(VideoCollect::getVideoId, videoIds))
                    .stream().map(VideoCollect::getVideoId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalCollected = collectedVideoIds;

        return videos.stream()
                .map(v -> VideoVO.from(v,
                        userMap.getOrDefault(v.getAuthorUserId(), null),
                        finalLiked.contains(v.getId()),
                        finalFollowed.contains(v.getAuthorUserId()),
                        finalCollected.contains(v.getId())))
                .toList();
    }

    @Override
    @Transactional
    public boolean toggleLike(Long userId, Long videoId) {
        if (userId == null || videoId == null) return false;

        // The relation unique key serializes competing toggles.  Do not use a
        // read-then-insert sequence: two requests can otherwise both observe
        // the relation as absent and increment the aggregate twice.
        Video video = getById(videoId);
        if (video == null) return false;

        if (likeMapper.insertIgnore(userId, videoId) > 0) {
            requireVideoCounterUpdate(baseMapper.incrementLike(videoId, 1), videoId, "like");
            updateAuthorFavorited(video.getAuthorUserId(), 1);
            invalidateRecommendationPools(userId);
            return true;
        }

        // A duplicate insert means the relation existed at the insert
        // statement's serialization point.  Only a successful delete is
        // allowed to decrement the aggregate (a concurrent toggle may have
        // removed it already).
        if (likeMapper.deleteByUserAndVideo(userId, videoId) > 0) {
            requireVideoCounterUpdate(baseMapper.incrementLike(videoId, -1), videoId, "like");
            updateAuthorFavorited(video.getAuthorUserId(), -1);
            invalidateRecommendationPools(userId);
            return false;
        }

        // Another request completed the toggle between our two statements;
        // report the currently visible relation without touching counters.
        return hasLiked(userId, videoId);
    }

    private void updateAuthorFavorited(Long authorId, int delta) {
        if (authorId == null || authorId <= 0) return;
        try {
            // Keep the creator aggregate on the same transaction and use an
            // SQL-side increment so concurrent likes cannot lose updates.
            userMapper.incrementTotalFavorited(authorId, delta);
        } catch (RuntimeException e) {
            log.error("updateAuthorFavorited failed: authorId={}, delta={}", authorId, delta, e);
            throw e;
        } catch (Exception e) {
            log.error("updateAuthorFavorited failed: authorId={}, delta={}", authorId, delta, e);
            throw new IllegalStateException("Unable to update creator like counter", e);
        }
    }

    private void requireVideoCounterUpdate(int rows, Long videoId, String counter) {
        if (rows != 1) {
            throw new IllegalStateException("Unable to update " + counter + " counter for video " + videoId);
        }
    }

    @Override
    public boolean hasLiked(Long userId, Long videoId) {
        return likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getVideoId, videoId)) > 0;
    }

    @Override
    @Transactional
    public long recordShare(Long videoId) {
        if (videoId == null) return 0;
        if (baseMapper.incrementShare(videoId, 1) != 1) return 0;
        Long count = baseMapper.selectShareCount(videoId);
        return count != null ? count : 0;
    }

    @Override
    @Transactional
    public boolean toggleCollect(Long userId, Long videoId) {
        if (userId == null || videoId == null) return false;
        Video video = getById(videoId);
        if (video == null) return false;

        if (collectMapper.insertIgnore(userId, videoId) > 0) {
            requireVideoCounterUpdate(baseMapper.incrementCollect(videoId, 1), videoId, "collect");
            invalidateRecommendationPools(userId);
            return true;
        }

        if (collectMapper.deleteByUserAndVideo(userId, videoId) > 0) {
            requireVideoCounterUpdate(baseMapper.incrementCollect(videoId, -1), videoId, "collect");
            invalidateRecommendationPools(userId);
            return false;
        }

        return collectMapper.selectCount(new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId)
                .eq(VideoCollect::getVideoId, videoId)) > 0;
    }

    @Override
    public PageDTO<VideoVO> getCollectedVideos(Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<VideoCollect> collectWrapper = new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId)
                .orderByDesc(VideoCollect::getCreateTime);
        IPage<VideoCollect> collectPage = collectMapper.selectPage(new Page<>(pageNo, pageSize), collectWrapper);
        List<Long> videoIds = collectPage.getRecords().stream()
                .map(VideoCollect::getVideoId).toList();

        if (videoIds.isEmpty()) return new PageDTO<>(collectPage.getTotal(), pageNo, pageSize, List.of());

        List<Video> videos = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a, LinkedHashMap::new));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get).filter(Objects::nonNull).toList();

        List<VideoVO> voList = toVideoVOList(ordered, userId);
        return new PageDTO<>(collectPage.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public List<VideoVO> searchVideos(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();
        try {
            List<Video> videos = searchService.search(keyword.trim(), 30);
            return toVideoVOList(videos, null);
        } catch (Exception e) {
            log.warn("多字段搜索失败, 回退简单搜索: {}", e.getMessage());
            List<Video> videos = baseMapper.searchByKeyword(keyword.trim());
            return toVideoVOList(videos, null);
        }
    }

    @Override
    @Transactional
    public void recordWatch(Long userId, Long videoId, Long authorUserId,
                            double watchDuration, double videoDuration, boolean finished,
                            String trafficSource, String sessionId, double swipeSeconds,
                            double lastPosition) {
        if (userId == null || videoId == null) return;
        double safeWatchDuration = finiteNonNegative(watchDuration);
        double safeVideoDuration = finiteNonNegative(videoDuration);
        double safeSwipeSeconds = finiteNonNegative(swipeSeconds);
        double safeLastPosition = finiteNonNegative(lastPosition);
        watchHistoryMapper.upsertProgress(
                userId,
                videoId,
                authorUserId != null ? authorUserId : 0L,
                safeWatchDuration,
                safeVideoDuration,
                finished ? 1 : 0,
                trafficSource,
                sessionId,
                safeSwipeSeconds,
                safeLastPosition);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    private void invalidateRecommendationPools(Long userId) {
        synchronized (this) {
            localRecommendationPools.keySet().removeIf(
                    key -> key.startsWith(String.valueOf(userId) + ":"));
        }
        if (redisCacheService != null) redisCacheService.invalidateRecommend(userId);
    }

    @Override
    public Double getLastPosition(Long userId, Long videoId) {
        if (userId == null || videoId == null) return 0.0;
        WatchHistory wh = watchHistoryMapper.selectOne(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .eq(WatchHistory::getVideoId, videoId)
                .select(WatchHistory::getLastPosition));
        if (wh == null || wh.getLastPosition() == null) return 0.0;
        // 留 1 秒余量, 避免刚好卡在上次结束帧
        return Math.max(0, wh.getLastPosition() - 1);
    }
}

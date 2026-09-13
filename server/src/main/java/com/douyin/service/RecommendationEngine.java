package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.entity.*;
import com.douyin.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎: 多路召回 → 多信号排序 → 多样性重排
 *
 * 召回通道: 内容召回 / 协同过滤 / 社交召回 / 热门召回 / 探索召回 / 创作者存量召回
 * 排序信号 (13 维): 内容匹配 / 视频质量 / 创作者亲和力 / 行为匹配 /
 *          社交关系 / 个人历史 / 热度趋势 / 新鲜度 / 用户类型加成 /
 *          探索激励 / 观看历史惩罚 / 社会证明 / 创作者互动
 *
 * 关键优化:
 * - 已点赞/收藏的视频高分惩罚（非硬过滤）：点赞 0.95，收藏 0.80
 * - 观看历史惩罚：看过的视频降权，完播未互动大幅降权
 * - 社会证明带时间衰减：旧视频的高赞数不会永久压制新视频 (30天半衰期)
 * - 创作者互动信号：你点赞/收藏过的作者发新视频自动提权
 * - 创作者存量召回：关注/互动过的作者中未刷到的优质旧视频也有曝光机会
 */
@Slf4j
@Service
public class RecommendationEngine {

    private static final int RECALL_PER_CHANNEL = RecommendationConfig.RECALL_PER_CHANNEL;
    private static final int CANDIDATE_POOL_SIZE = RecommendationConfig.CANDIDATE_POOL_SIZE;
    private static final int MAX_BEHAVIOR_HISTORY = 2000;
    private static final int MAX_EXPOSURE_EXCLUDES = 2000;

    private volatile List<String> cachedTrendingKeywords = List.of();
    private volatile long trendingKeywordsCacheTime = 0;

    private final VideoMapper videoMapper;
    private final VideoContentMapper contentMapper;
    private final VideoExposureMapper exposureMapper;
    private final UserContentProfileMapper profileMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoCollectMapper collectMapper;
    private final UserMapper userMapper;
    private final UserProfileService profileService;
    private final ObjectMapper objectMapper;

    public RecommendationEngine(VideoMapper videoMapper, VideoContentMapper contentMapper,
                                VideoExposureMapper exposureMapper, UserContentProfileMapper profileMapper,
                                LikeMapper likeMapper, FollowMapper followMapper,
                                WatchHistoryMapper watchHistoryMapper,
                                VideoCollectMapper collectMapper, UserMapper userMapper,
                                UserProfileService profileService) {
        this.videoMapper = videoMapper;
        this.contentMapper = contentMapper;
        this.exposureMapper = exposureMapper;
        this.profileMapper = profileMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.collectMapper = collectMapper;
        this.userMapper = userMapper;
        this.profileService = profileService;
        this.objectMapper = new ObjectMapper();
    }

    /** Legacy HOME entry point. */
    public List<Long> recommend(Long userId, int pageSize, Double minDuration) {
        return recommend(userId, pageSize, minDuration, FeedChannel.HOME);
    }

    /**
     * Main recommendation entry point.  HOT is deliberately handled outside
     * the personalized engine so a logged-in user cannot accidentally turn
     * the global trending feed into HOME.
     */
    public List<Long> recommend(Long userId, int pageSize, Double minDuration, FeedChannel channel) {
        return recommendInternal(userId, pageSize, minDuration, channel, true);
    }

    /** Build a bounded candidate pool without marking every candidate exposed. */
    public List<Long> recommendPool(Long userId, int poolSize, Double minDuration, FeedChannel channel) {
        return recommendInternal(userId, poolSize, minDuration, channel, false);
    }

    private List<Long> recommendInternal(Long userId, int pageSize, Double minDuration,
                                         FeedChannel channel, boolean recordExposure) {
        pageSize = Math.min(CANDIDATE_POOL_SIZE, Math.max(1, pageSize));
        FeedChannel effectiveChannel = channel == null ? FeedChannel.HOME : channel;
        if (effectiveChannel == FeedChannel.HOT) {
            return hotOnly(userId, pageSize, minDuration, recordExposure);
        }

        if (effectiveChannel == FeedChannel.FOLLOWING) {
            return followingOnly(userId, pageSize, minDuration, recordExposure);
        }

        if (effectiveChannel == FeedChannel.FRIENDS) {
            return friendsOnly(userId, pageSize, minDuration, recordExposure);
        }

        // Live rooms have a separate media index/API. Do not silently serve
        // VOD when that index is unavailable.
        if (effectiveChannel == FeedChannel.LIVE) {
            return List.of();
        }

        // EXPERIENCE intentionally increases the deterministic exploration
        // share; LONG_VIDEO is still personalized but constrained by duration.
        return recommendPersonalized(userId, pageSize, minDuration, effectiveChannel, recordExposure);
    }

    private List<Long> recommendPersonalized(Long userId, int pageSize, Double minDuration,
                                             FeedChannel channel, boolean recordExposure) {
        // 0. 获取已互动视频 ID + 互动作者集合
        int recentCount = 0, likedCount = 0, collectedCount = 0, watchedCount = 0;

        // 硬过滤：24h 内曝光过的
        Set<Long> recentExposureIds = getRecentExposures(userId);
        Set<Long> excludeIds = new HashSet<>(recentExposureIds);
        recentCount = recentExposureIds.size();

        // 软惩罚：已点赞/收藏的视频不硬排除，但给时间衰减惩罚
        Map<Long, LocalDateTime> likedMap = getLikedVideoMap(userId);
        likedCount = likedMap.size();

        Map<Long, LocalDateTime> collectedMap = getCollectedVideoMap(userId);
        collectedCount = collectedMap.size();

        // 硬过滤：N 天内完整观看过的
        Set<Long> recentWatchedIds = getFullyWatchedVideoIds(userId, RecommendationConfig.DAYS_RECENT_WATCHED);
        watchedCount = recentWatchedIds.size();
        excludeIds.addAll(recentWatchedIds);

        // 软惩罚：N 天内完整观看过的 (不硬排除，但降权)
        Set<Long> olderWatchedIds = getFullyWatchedVideoIds(userId, RecommendationConfig.DAYS_OLDER_WATCHED);
        olderWatchedIds.removeAll(recentWatchedIds);

        // 互动过的作者ID集合 (点赞/收藏过其内容 → 不是自己的内容)
        Set<Long> interactedAuthorIds = getInteractedAuthorIds(userId, likedMap, collectedMap);

        UserContentProfile profile = profileMapper.selectById(userId);
        boolean coldStart = isColdStart(profile, userId);

        // 1. 多路召回 (6 路)
        List<Long> cfCandidates = collaborativeRecall(userId, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> socialCandidates = socialRecall(userId, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> contentCandidates = contentRecall(profile, excludeIds, RECALL_PER_CHANNEL, minDuration);
        List<Long> hotCandidates = hotRecall(excludeIds, RECALL_PER_CHANNEL, minDuration);
        int exploreLimit = channel == FeedChannel.EXPERIENCE
                ? RECALL_PER_CHANNEL * 2 : RECALL_PER_CHANNEL;
        List<Long> exploreCandidates = exploreRecall(profile, excludeIds, exploreLimit, minDuration);
        List<Long> backlogCandidates = creatorBacklogRecall(userId, excludeIds, interactedAuthorIds,
                RECALL_PER_CHANNEL, minDuration);
        // A cold account has no evidence for account-specific ordering yet.
        // Use the bounded quality/freshness order from the recall query; do
        // not manufacture personalization from the account ID.
        List<Long> coldStartCandidates = coldStartRecall(excludeIds,
                RECALL_PER_CHANNEL, minDuration);

        // A candidate recalled by a relevance/social/hot route remains in the
        // main rank. Only exploration-exclusive candidates consume explicit
        // exploration slots; multi-route evidence must never demote content.
        Set<Long> mainRecallIds = new HashSet<>();
        mainRecallIds.addAll(backlogCandidates);
        if (coldStart) mainRecallIds.addAll(coldStartCandidates);
        mainRecallIds.addAll(contentCandidates);
        mainRecallIds.addAll(cfCandidates);
        mainRecallIds.addAll(socialCandidates);
        mainRecallIds.addAll(hotCandidates);
        List<Long> explorationOnlyCandidates = exploreCandidates.stream()
                .filter(id -> !mainRecallIds.contains(id))
                .toList();

        // 2. 合并去重 — 创作者存量召回优先排在前面
        Set<Long> candidateSet = new LinkedHashSet<>();
        candidateSet.addAll(backlogCandidates);
        if (coldStart) candidateSet.addAll(coldStartCandidates);
        candidateSet.addAll(contentCandidates);
        candidateSet.addAll(cfCandidates);
        candidateSet.addAll(socialCandidates);
        candidateSet.addAll(hotCandidates);
        candidateSet.addAll(exploreCandidates);
        candidateSet.removeAll(excludeIds);

        if (candidateSet.size() < pageSize) {
            List<Video> fallback = videoMapper.findRecallCandidates(
                    new ArrayList<>(excludeIds), minDuration, RECALL_PER_CHANNEL);
            fallback.forEach(v -> candidateSet.add(v.getId()));
        }

        // A small catalogue can be entirely covered by the 24-hour exposure
        // window. Falling through to the service's global HOT fallback would
        // discard the user's measured profile and make every account see the
        // same order. Once no unseen candidate remains, recycle exposed items
        // but keep recently completed videos excluded and run the normal
        // evidence-based ranker over the recycled pool.
        if (candidateSet.size() < pageSize && !recentExposureIds.isEmpty()) {
            List<Video> recycled = videoMapper.findRecallCandidates(
                    new ArrayList<>(recentWatchedIds), minDuration, CANDIDATE_POOL_SIZE);
            recycled.forEach(v -> candidateSet.add(v.getId()));
            log.debug("推荐未曝光候选不足，回收已曝光内容继续个性化排序: userId={} recycled={}",
                    userId, candidateSet.size());
        }

        List<Long> candidates = new ArrayList<>(candidateSet);
        if (candidates.isEmpty()) return List.of();

        // 3. 批量加载候选视频 + 内容特征 + 观看历史 + 作者粉丝数
        List<Video> videos = videoMapper.selectBatchIds(
                candidates.subList(0, Math.min(candidates.size(), CANDIDATE_POOL_SIZE)));
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());
        Map<Long, WatchHistory> watchMap = loadWatchMap(userId,
                videos.stream().map(Video::getId).toList());
        Map<Long, Long> authorFollowerMap = loadAuthorFollowerMap(
                videos.stream().map(Video::getAuthorUserId).filter(Objects::nonNull).distinct().toList());

        // 近期互动数据 (用于动态时效判断)
        Map<Long, RecentEngagement> recentEngMap = loadRecentEngagement(
                videos.stream().map(Video::getId).toList());

        // 全站完播率 (用于质量信号)
        Map<Long, Double> avgCompletionMap = loadAvgCompletion(
                videos.stream().map(Video::getId).toList());

        // 4. 多信号打分 (13 维)
        List<ScoredVideo> scored = scoreVideos(userId, videos, contentMap, watchMap,
                authorFollowerMap, profile, likedMap, collectedMap, interactedAuthorIds,
                recentEngMap, avgCompletionMap, olderWatchedIds);

        // Only measured relevance signals may change the order between users.
        // Equal scores use a global stable tie-break, so two accounts with the
        // same evidence are allowed to receive the same feed.
        scored.sort(Comparator
                .comparingDouble(ScoredVideo::score).reversed()
                .thenComparing(ScoredVideo::videoId,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        // 构建视频年龄映射 (小时)
        Map<Long, Long> videoAgeHours = videos.stream()
                .filter(v -> v.getCreateTime() != null)
                .collect(Collectors.toMap(Video::getId,
                        v -> ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now())));

        // 构建视频类型映射 (用于内容类型多样性)
        Map<Long, String> videoTypeMap = videos.stream()
                .filter(v -> v.getType() != null)
                .collect(Collectors.toMap(Video::getId, Video::getType, (a, b) -> a));

        // 5. 多样性重排
        List<Long> result = reRank(scored, contentMap, explorationOnlyCandidates,
                pageSize, videoAgeHours, videoTypeMap);

        // 6. 记录曝光
        if (recordExposure) recordExposuresBatch(userId, result);

        log.info("推荐完成: userId={} candidates={} exclude[recent={} liked={} collected={} watched={}] interactedAuthors={} result={}",
                userId, candidates.size(),
                recentCount, likedCount, collectedCount, watchedCount,
                interactedAuthorIds.size(), result.size());

        return result;
    }

    /** Global HOT channel: no profile, follows, likes, or account-specific ordering. */
    private List<Long> hotOnly(Long userId, int pageSize, Double minDuration, boolean recordExposure) {
        Set<Long> excludes = userId == null ? Set.of() : getRecentExposures(userId);
        List<Long> result = hotRecall(excludes, Math.max(1, pageSize), minDuration);
        if (recordExposure && userId != null) recordExposuresBatch(userId, result);
        return result;
    }

    /** FOLLOWING channel: only authors explicitly followed by the viewer. */
    private List<Long> followingOnly(Long userId, int pageSize, Double minDuration, boolean recordExposure) {
        if (userId == null) return List.of();
        List<Long> followed = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                        .orderByDesc(Follow::getCreateTime)
                        .orderByDesc(Follow::getId)
                        .last("LIMIT " + RecommendationConfig.SOCIAL_FOLLOW_LIMIT))
                .stream()
                .map(Follow::getFollowId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (followed.isEmpty()) return List.of();

        Set<Long> excludes = getRecentExposures(userId);
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, followed)
                .notIn(Video::getId, excludes.isEmpty() ? Set.of(-1L) : excludes)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT " + Math.max(1, pageSize));
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        List<Long> result = videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
        if (recordExposure) recordExposuresBatch(userId, result);
        return result;
    }

    /** FRIENDS channel: videos authored by mutual-follow accounts. */
    private List<Long> friendsOnly(Long userId, int pageSize, Double minDuration, boolean recordExposure) {
        if (userId == null) return List.of();
        List<Long> friendIds = followMapper.findMutualFollowIds(userId,
                RecommendationConfig.SOCIAL_FOLLOW_LIMIT);
        if (friendIds == null || friendIds.isEmpty()) return List.of();

        Set<Long> excludes = getRecentExposures(userId);
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, friendIds)
                .notIn(Video::getId, excludes.isEmpty() ? Set.of(-1L) : excludes)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT " + Math.max(1, pageSize));
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        List<Long> result = videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
        if (recordExposure) recordExposuresBatch(userId, result);
        return result;
    }

    private boolean isColdStart(UserContentProfile profile, Long userId) {
        if (profile == null) return true;
        int views = profile.getTotalViewCount() != null ? profile.getTotalViewCount() : 0;
        int watches = profile.getTotalWatchCount() != null ? profile.getTotalWatchCount() : 0;
        return views < RecommendationConfig.SEG_NEW_USER
                && watches < RecommendationConfig.SEG_NEW_USER;
    }

    /** Pull a bounded quality/freshness pool for accounts with no behavior yet. */
    private List<Long> coldStartRecall(Set<Long> excludeIds, int limit,
                                       Double minDuration) {
        int broadLimit = Math.min(800, Math.max(limit * 8, 160));
        List<Video> pool = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, broadLimit);
        if (pool.isEmpty()) return List.of();
        return pool.stream()
                .limit(Math.max(1, limit))
                .map(Video::getId)
                .toList();
    }

    // ===================== 已互动内容映射 =====================

    /** 用户已点赞的视频 → 点赞时间 (用于时间衰减惩罚) */
    private Map<Long, LocalDateTime> getLikedVideoMap(Long userId) {
        return likeMapper.findRecentLikes(userId, MAX_BEHAVIOR_HISTORY)
                .stream()
                .filter(l -> l.getCreateTime() != null)
                .collect(Collectors.toMap(Like::getVideoId, Like::getCreateTime, (a, b) -> a));
    }

    /** 用户已收藏的视频 → 收藏时间 (用于时间衰减惩罚) */
    private Map<Long, LocalDateTime> getCollectedVideoMap(Long userId) {
        return collectMapper.findRecentCollects(userId, MAX_BEHAVIOR_HISTORY)
                .stream()
                .filter(c -> c.getCreateTime() != null)
                .collect(Collectors.toMap(VideoCollect::getVideoId, VideoCollect::getCreateTime, (a, b) -> a));
    }

    /** 最近 N 天内完整观看过的视频 ID 集合 */
    private Set<Long> getFullyWatchedVideoIds(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return new HashSet<>(watchHistoryMapper.findRecentFinishedVideoIds(
                userId, since, MAX_BEHAVIOR_HISTORY));
    }

    /** 用户互动过的作者 ID 集合 (通过点赞/收藏的视频反查作者) */
    private Set<Long> getInteractedAuthorIds(Long userId, Map<Long, LocalDateTime> likedMap,
                                              Map<Long, LocalDateTime> collectedMap) {
        Set<Long> videoIds = new HashSet<>();
        videoIds.addAll(likedMap.keySet());
        videoIds.addAll(collectedMap.keySet());

        if (videoIds.isEmpty()) return Set.of();

        return videoMapper.selectBatchIds(videoIds).stream()
                .map(Video::getAuthorUserId)
                .filter(Objects::nonNull)
                .filter(aid -> !aid.equals(userId))
                .collect(Collectors.toSet());
    }

    // ===================== 批量加载辅助数据 =====================

    /** 批量加载观看历史 (userId → candidateVideoId → WatchHistory) */
    private Map<Long, WatchHistory> loadWatchMap(Long userId, List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        // 取最近一次对该视频的观看记录
        return watchHistoryMapper.selectList(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .in(WatchHistory::getVideoId, videoIds)
                .orderByDesc(WatchHistory::getUpdateTime)
                .orderByDesc(WatchHistory::getId))
                .stream()
                .collect(Collectors.toMap(
                        WatchHistory::getVideoId, w -> w,
                        (existing, replacement) -> existing)); // 保留第一条 (最新)
    }

    /** 批量加载作者粉丝数 */
    private Map<Long, Long> loadAuthorFollowerMap(List<Long> authorIds) {
        if (authorIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(authorIds).stream()
                .filter(u -> u.getFollowerCount() != null)
                .collect(Collectors.toMap(User::getUid, User::getFollowerCount));
    }

    /** 批量加载近期互动数据: 每个候选视频近7天的点赞数/观看人次/曝光次数 */
    private Map<Long, RecentEngagement> loadRecentEngagement(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        LocalDateTime since = LocalDateTime.now().minusDays(RecommendationConfig.DAYS_RECENT_ENGAGEMENT);

        Map<Long, Integer> recentLikes = new HashMap<>();
        try {
            likeMapper.countRecentLikes(videoIds, since).forEach(row -> {
                Long vid = Long.valueOf(row.get("video_id").toString());
                Integer cnt = Integer.valueOf(row.get("cnt").toString());
                recentLikes.put(vid, cnt);
            });
        } catch (Exception ignored) {}

        Map<Long, Integer> recentWatches = new HashMap<>();
        try {
            watchHistoryMapper.countRecentWatches(videoIds, since).forEach(row -> {
                Long vid = Long.valueOf(row.get("video_id").toString());
                Integer cnt = Integer.valueOf(row.get("cnt").toString());
                recentWatches.put(vid, cnt);
            });
        } catch (Exception ignored) {}

        // 组装
        Map<Long, RecentEngagement> result = new HashMap<>();
        for (Long vid : videoIds) {
            result.put(vid, new RecentEngagement(
                    recentLikes.getOrDefault(vid, 0),
                    recentWatches.getOrDefault(vid, 0)));
        }
        return result;
    }

    /** 批量加载全站平均完播率 */
    private Map<Long, Double> loadAvgCompletion(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        try {
            return watchHistoryMapper.avgCompletionRate(videoIds).stream()
                    .collect(Collectors.toMap(
                            row -> Long.valueOf(row.get("video_id").toString()),
                            row -> Double.valueOf(row.get("avg_comp").toString()),
                            (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 近期互动数据 */
    private record RecentEngagement(int recentLikes, int recentWatches) {
        double pulse() {
            if (recentLikes == 0 && recentWatches == 0) return 0.0;
            double likePulse = Math.min(1.0, recentLikes / RecommendationConfig.PULSE_LIKE_NORM);
            double watchPulse = Math.min(1.0, recentWatches / RecommendationConfig.PULSE_WATCH_NORM);
            return likePulse * RecommendationConfig.PULSE_LIKE_W + watchPulse * RecommendationConfig.PULSE_WATCH_W;
        }

        boolean isActive() {
            return recentLikes >= RecommendationConfig.RE_ACTIVE_LIKE_MIN
                    && recentWatches >= RecommendationConfig.RE_ACTIVE_WATCH_MIN;
        }
    }

    // ===================== 多路召回 =====================

    /** 内容召回: 基于用户品类偏好 + 内容向量相似度 */
    private List<Long> contentRecall(UserContentProfile profile, Set<Long> excludeIds,
                                      int limit, Double minDuration) {
        List<Video> videos = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, limit * RecommendationConfig.RECALL_OVERSAMPLE_CONTENT);
        String profileVector = profileVector(profile);
        if (profileVector == null || videos.isEmpty()) {
            return videos.stream().map(Video::getId).limit(limit).toList();
        }

        List<Double> userVec = parseVector(profileVector);
        if (userVec == null) return videos.stream().map(Video::getId).limit(limit).toList();

        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        Map<Long, VideoContent> contentMap = loadContentMap(videoIds);

        return videos.stream()
                .map(v -> {
                    double sim = cosineSim(userVec, contentMap.get(v.getId()));
                    double catBonus = categoryBonus(profile, contentMap.get(v.getId()));
                    return new CandidateScore(v.getId(), sim + catBonus);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed()
                        .thenComparing(CandidateScore::videoId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    /** 协同过滤召回: Item-based CF —— 共同被赞的视频 */
    private List<Long> collaborativeRecall(Long userId, Set<Long> excludeIds, int limit, Double minDuration) {
        List<Long> likedIds = likeMapper.findRecentLikedVideoIds(userId, RecommendationConfig.CF_RECENT_LIKED_LIMIT);
        if (likedIds.isEmpty()) return List.of();

        List<Map<String, Object>> rows = likeMapper.findCoLikedVideoIds(
                likedIds, new ArrayList<>(excludeIds), limit * RecommendationConfig.RECALL_OVERSAMPLE_CF);
        if (rows.isEmpty()) return List.of();

        // 验证视频有效性: 过滤已删除/非公开/时长不足的视频
        List<Long> candidateIds = rows.stream()
                .map(r -> Long.valueOf(r.get("video_id").toString()))
                .toList();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getId, candidateIds)
                .eq(Video::getStatus, "APPROVED")
                .in(Video::getType, List.of("recommend-video", "image", "text"));
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        Set<Long> validIds = videoMapper.selectList(wrapper).stream()
                .map(Video::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Keep the co-like score order returned by findCoLikedVideoIds.  An
        // unordered IN query with LIMIT would otherwise select an arbitrary
        // subset before the ranker has a chance to score it.
        return candidateIds.stream()
                .filter(validIds::contains)
                .distinct()
                .limit(limit)
                .toList();
    }

    /** 社交召回: 关注作者 + 最近互动作者的新视频 */
    private List<Long> socialRecall(Long userId, Set<Long> excludeIds, int limit, Double minDuration) {
        Set<Long> authorIds = new LinkedHashSet<>();

        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .orderByDesc(Follow::getCreateTime)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + RecommendationConfig.SOCIAL_FOLLOW_LIMIT));
        follows.forEach(f -> authorIds.add(f.getFollowId()));

        List<Long> recentAuthors = videoMapper.findRecentAuthorIds(userId, RecommendationConfig.SOCIAL_RECENT_AUTHOR_LIMIT);
        authorIds.addAll(recentAuthors);

        if (authorIds.isEmpty()) return List.of();

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, authorIds)
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT " + limit);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 热门召回: 高互动量 + 高质量分 */
    private List<Long> hotRecall(Set<Long> excludeIds, int limit, Double minDuration) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        wrapper.last("LIMIT " + limit);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    /** 探索召回: 用户未充分接触的品类 + 质量/新鲜度候选。 */
    private List<Long> exploreRecall(UserContentProfile profile, Set<Long> excludeIds,
                                      int limit, Double minDuration) {
        List<Video> videos = videoMapper.findRecallCandidates(
                new ArrayList<>(excludeIds), minDuration, limit * RecommendationConfig.RECALL_OVERSAMPLE_EXPLORE);

        if (profile == null || profile.getCategoryWeights() == null) {
            return videos.stream()
                    .map(Video::getId).limit(limit).toList();
        }

        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        Map<Long, VideoContent> contentMap = loadContentMap(
                videos.stream().map(Video::getId).toList());

        return videos.stream()
                .map(v -> {
                    VideoContent vc = contentMap.get(v.getId());
                    String cat = vc != null ? vc.getTextCategory() : null;
                    double exploreScore = cat != null
                            ? (1.0 - catWeights.getOrDefault(cat, 0.0))
                            : RecommendationConfig.DEF_SCORE_HALF;
                    // findRecallCandidates already orders equal-score items by
                    // extraction quality and freshness. Stream sorting is
                    // stable, so this preserves that evidence-based order.
                    return new CandidateScore(v.getId(), exploreScore);
                })
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed()
                        .thenComparing(CandidateScore::videoId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(CandidateScore::videoId)
                .toList();
    }

    /**
     * 创作者存量召回: 关注/互动过的作者中，用户未刷到过的优质旧视频。
     * 解决"关注的人发了优质内容但我没刷到"的问题。
     */
    private List<Long> creatorBacklogRecall(Long userId, Set<Long> excludeIds,
                                             Set<Long> interactedAuthorIds,
                                             int limit, Double minDuration) {
        Set<Long> authorIds = new LinkedHashSet<>();
        // 关注作者
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .orderByDesc(Follow::getCreateTime)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + RecommendationConfig.BACKLOG_FOLLOW_LIMIT));
        follows.forEach(f -> authorIds.add(f.getFollowId()));
        // 互动作者
        authorIds.addAll(interactedAuthorIds);

        if (authorIds.isEmpty()) return List.of();

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, authorIds)
                .notIn(Video::getId, excludeIds.isEmpty() ? Set.of(-1L) : excludeIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .le(Video::getCreateTime, LocalDateTime.now().minusDays(RecommendationConfig.DAYS_BACKLOG_MIN))
                .ge(Video::getCreateTime, LocalDateTime.now().minusDays(RecommendationConfig.DAYS_BACKLOG_MAX))
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime)
                .orderByDesc(Video::getId)
                .last("LIMIT " + limit);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        return videoMapper.selectList(wrapper).stream().map(Video::getId).toList();
    }

    // ===================== 多信号打分 (13 维) =====================

    private List<ScoredVideo> scoreVideos(Long userId, List<Video> videos,
                                           Map<Long, VideoContent> contentMap,
                                           Map<Long, WatchHistory> watchMap,
                                           Map<Long, Long> authorFollowerMap,
                                           UserContentProfile profile,
                                           Map<Long, LocalDateTime> likedMap,
                                           Map<Long, LocalDateTime> collectedMap,
                                           Set<Long> interactedAuthorIds,
                                           Map<Long, RecentEngagement> recentEngMap,
                                           Map<Long, Double> avgCompletionMap,
                                           Set<Long> olderWatchedIds) {
        if (profile == null) profile = profileService.getOrCreate(userId);
        // A missing profile is a valid cold-start state.  Keep ranking on the
        // global quality/freshness signals until the first behavior event,
        // instead of failing the feed request or inventing account-specific
        // ordering.
        if (profile == null) {
            profile = new UserContentProfile();
            profile.setUserId(userId);
            profile.setUserType("balanced");
        }

        List<Double> userVec = parseVector(profileVector(profile));
        List<Double> userVecShort = parseVector(profile != null ? profile.getShortTermVector() : null);
        Map<String, Double> creatorAffinity = parseCreatorAffinityMap(profile.getCreatorAffinity());
        List<String> recentSearches = parseRecentSearches(profile.getRecentSearchQueries());
        Map<String, Double> catWeights = parseCategoryWeights(profile.getCategoryWeights());
        String userType = profile.getUserType() != null ? profile.getUserType() : "balanced";
        Map<Long, Double> authorAffinityCache = new HashMap<>();
        Set<Long> followedSet = getFollowedSet(userId);

        // 品类反馈: 一次查询完成正反馈+负反馈
        CategoryFeedback catFeedback = calcCategoryFeedback(userId);
        Map<String, Double> catSkipPenalties = catFeedback.skipPenalties();
        Map<String, Double> catLikeBoosts = catFeedback.likeBoosts();
        // 全局搜索趋势关键词
        List<String> trendingKeywords = getTrendingKeywords(RecommendationConfig.LIMIT_TRENDING_KEYWORDS);

        List<ScoredVideo> scored = new ArrayList<>(videos.size());
        for (Video v : videos) {
            Long vid = v.getId();
            VideoContent vc = contentMap.get(vid);
            WatchHistory wh = watchMap.get(vid);
            Long authorFollowerCount = authorFollowerMap.getOrDefault(v.getAuthorUserId(), 0L);

            double contentMatch = calcContentMatch(userVec, userVecShort, vc);
            double avgComp = avgCompletionMap.getOrDefault(vid, RecommendationConfig.DEF_COMPLETION);
            double qualityScore = calcQualityScore(v, vc, authorFollowerCount, avgComp);
            double creatorAff = calcCreatorAffinity(v.getAuthorUserId(), creatorAffinity);
            double behavioral = calcBehavioralMatch(v, vc, recentSearches, catWeights, userType,
                    trendingKeywords);
            double socialScore = calcSocialScore(userId, v, authorAffinityCache, followedSet);
            double personalHist = calcPersonalHistory(userId, v, vc, profile);
            double popularity = calcPopularityTrend(v);
            RecentEngagement re = recentEngMap.getOrDefault(vid, new RecentEngagement(0, 0));
            double freshness = calcFreshness(v, re);
            double userTypeBonus = calcUserTypeBonus(userType, v, vc, profile);
            double exploration = calcExplorationBonus(v, vc, catWeights, profile);
            double watchPenalty = calcWatchHistoryPenalty(wh, v, profile);
            double socialProof = calcSocialProof(v, authorFollowerCount, re);
            double creatorInteraction = calcCreatorInteraction(v, followedSet, interactedAuthorIds);

            // 互动惩罚 (时间衰减): 刚赞/收藏的少推
            double interactionPenalty = calcInteractionPenalty(vid, likedMap, collectedMap);

            // 完播软惩罚 (1~7 天内完整看过 → 降权但不禁)
            double watchedPenalty = olderWatchedIds.contains(vid) ? RecommendationConfig.PEN_OLDER_WATCHED : 0.0;

            // 品类负反馈: 连续快划过的品类降权
            double categoryPenalty = 0.0;
            if (vc != null && vc.getTextCategory() != null) {
                categoryPenalty = catSkipPenalties.getOrDefault(vc.getTextCategory(), 0.0);
            }

            // 品类正反馈: 用户完整看完的品类提权
            double categoryBoost = 0.0;
            if (vc != null && vc.getTextCategory() != null) {
                categoryBoost = catLikeBoosts.getOrDefault(vc.getTextCategory(), 0.0);
            }

            double score = RecommendationConfig.W_CONTENT * contentMatch
                    + RecommendationConfig.W_QUALITY * qualityScore
                    + RecommendationConfig.W_CREATOR_AFFINITY * creatorAff
                    + RecommendationConfig.W_BEHAVIORAL * behavioral
                    + RecommendationConfig.W_SOCIAL * socialScore
                    + RecommendationConfig.W_PERSONAL_HISTORY * personalHist
                    + RecommendationConfig.W_POPULARITY * popularity
                    + RecommendationConfig.W_FRESHNESS * freshness
                    + RecommendationConfig.W_USER_TYPE * userTypeBonus
                    + RecommendationConfig.W_EXPLORATION * exploration
                    - RecommendationConfig.W_WATCH_PENALTY * watchPenalty
                    + RecommendationConfig.W_SOCIAL_PROOF * socialProof
                    + RecommendationConfig.W_CREATOR_INTERACTION * creatorInteraction
                    - interactionPenalty
                    - watchedPenalty
                    - categoryPenalty
                    + categoryBoost;

            scored.add(new ScoredVideo(vid, v.getAuthorUserId(),
                    vc != null ? vc.getTextCategory() : null, score,
                    contentMatch, qualityScore, creatorAff, behavioral,
                    socialScore, personalHist, popularity, freshness,
                    userTypeBonus, exploration, watchPenalty, socialProof,
                    creatorInteraction));
        }

        scored.sort(Comparator.comparingDouble(ScoredVideo::score).reversed());
        return scored;
    }

    /**
     * 互动惩罚 (时间衰减): 点赞/收藏过同一视频的惩罚随天数递减。
     * 刚赞过的视频几乎不再推, 但 30 天前赞的只给轻微惩罚, 90 天前基本不惩罚。
     */
    private double calcInteractionPenalty(Long videoId,
                                           Map<Long, LocalDateTime> likedMap,
                                           Map<Long, LocalDateTime> collectedMap) {
        LocalDateTime likedAt = likedMap.get(videoId);
        LocalDateTime collectedAt = collectedMap.get(videoId);

        if (likedAt != null) {
            long daysAgo = ChronoUnit.DAYS.between(likedAt, LocalDateTime.now());
            double decay = Math.max(RecommendationConfig.PEN_INTERACTION_FLOOR,
                    ScoringFunctions.expDecay(daysAgo * 24, RecommendationConfig.HL_INTERACTION));
            return RecommendationConfig.PEN_LIKED_BASE * decay;
        }
        if (collectedAt != null) {
            long daysAgo = ChronoUnit.DAYS.between(collectedAt, LocalDateTime.now());
            double decay = Math.max(RecommendationConfig.PEN_INTERACTION_FLOOR,
                    ScoringFunctions.expDecay(daysAgo * 24, RecommendationConfig.HL_INTERACTION));
            return RecommendationConfig.PEN_COLLECTED_BASE * decay;
        }
        return 0.0;
    }

    /**
     * 品类反馈 (合并查询): 一次查询 watch_history + 一次查询 video_content，
     * 同时计算正反馈 (完整看完) 和负反馈 (快速划走)。
     */
    private CategoryFeedback calcCategoryFeedback(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(RecommendationConfig.DAYS_CATEGORY_FEEDBACK);
        List<WatchHistory> recentWatches = watchHistoryMapper.findRecentCategoryFeedback(
                userId, since, RecommendationConfig.LIMIT_CATEGORY_FEEDBACK_HISTORY);
        if (recentWatches.isEmpty()) return new CategoryFeedback(Map.of(), Map.of());

        // 拆分为快划和完整观看两组
        List<Long> skippedIds = new ArrayList<>();
        List<Long> wellWatchedIds = new ArrayList<>();
        for (WatchHistory w : recentWatches) {
            Double swipe = w.getSwipeSeconds();
            if (swipe != null && swipe < RecommendationConfig.UP_BOUNCE_THRESHOLD_SEC) {
                skippedIds.add(w.getVideoId());
            }
            Double wd = w.getWatchDuration();
            Double vd = w.getVideoDuration();
            if (wd != null && vd != null && vd > 0 && (wd / vd) > RecommendationConfig.CAT_WELL_WATCHED_COMPLETION) {
                wellWatchedIds.add(w.getVideoId());
            }
        }

        // 一次查询两个集合的视频品类
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(skippedIds);
        allIds.addAll(wellWatchedIds);
        if (allIds.isEmpty()) return new CategoryFeedback(Map.of(), Map.of());

        List<VideoContent> contents = contentMapper.selectList(
                new LambdaQueryWrapper<VideoContent>()
                        .in(VideoContent::getVideoId, allIds)
                        .isNotNull(VideoContent::getTextCategory));

        Set<Long> skipSet = new HashSet<>(skippedIds);
        Set<Long> wellSet = new HashSet<>(wellWatchedIds);
        Map<String, Integer> skipCounts = new HashMap<>();
        Map<String, Integer> likeCounts = new HashMap<>();
        for (VideoContent vc : contents) {
            String cat = vc.getTextCategory();
            if (cat == null) continue;
            if (skipSet.contains(vc.getVideoId())) skipCounts.merge(cat, 1, Integer::sum);
            if (wellSet.contains(vc.getVideoId())) likeCounts.merge(cat, 1, Integer::sum);
        }

        Map<String, Double> penalties = new HashMap<>();
        for (var entry : skipCounts.entrySet()) {
            int count = entry.getValue();
            if (count >= RecommendationConfig.CAT_SKIP_COUNT_THRESHOLD) {
                penalties.put(entry.getKey(), Math.min(RecommendationConfig.CAT_SKIP_CEIL, count * RecommendationConfig.CAT_SKIP_PER));
            }
        }

        Map<String, Double> boosts = new HashMap<>();
        for (var entry : likeCounts.entrySet()) {
            int count = entry.getValue();
            if (count >= RecommendationConfig.CAT_LIKE_COUNT_THRESHOLD) {
                boosts.put(entry.getKey(), Math.min(RecommendationConfig.CAT_LIKE_CEIL, count * RecommendationConfig.CAT_LIKE_PER));
            }
        }

        return new CategoryFeedback(penalties, boosts);
    }

    /**
     * 全局搜索趋势: 从最近活跃用户的搜索记录中提取高频关键词。
     * 如果最近很多人在搜"世界杯"，相关视频(哪怕是旧的)应该被唤醒。
     */
    private List<String> getTrendingKeywords(int topN) {
        long now = System.currentTimeMillis();
        if (now - trendingKeywordsCacheTime < RecommendationConfig.TRENDING_CACHE_MS) {
            return cachedTrendingKeywords;
        }
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(RecommendationConfig.DAYS_TRENDING_KEYWORD);
            List<UserContentProfile> recentProfiles = profileMapper.selectList(
                    new LambdaQueryWrapper<UserContentProfile>()
                            .ge(UserContentProfile::getUpdateTime, since)
                            .isNotNull(UserContentProfile::getRecentSearchQueries)
                            .orderByDesc(UserContentProfile::getUpdateTime)
                            .orderByDesc(UserContentProfile::getUserId)
                            .last("LIMIT 200"));
            Map<String, Integer> keywordFreq = new HashMap<>();
            for (UserContentProfile p : recentProfiles) {
                List<String> queries = parseRecentSearches(p.getRecentSearchQueries());
                for (String q : queries) {
                    if (q != null && q.length() >= RecommendationConfig.KEYWORD_MIN_LEN) {
                        keywordFreq.merge(q.toLowerCase(), 1, Integer::sum);
                    }
                }
            }
            List<String> result = keywordFreq.entrySet().stream()
                    .filter(e -> e.getValue() >= RecommendationConfig.LIMIT_TRENDING_MIN_FREQ)
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .limit(topN)
                    .map(Map.Entry::getKey)
                    .toList();
            cachedTrendingKeywords = result;
            trendingKeywordsCacheTime = now;
            return result;
        } catch (Exception e) {
            cachedTrendingKeywords = List.of();
            trendingKeywordsCacheTime = now;
            return List.of();
        }
    }

    // --- 各信号计算方法 ---

    /** 内容匹配: 长期+短期向量双路余弦相似度 + 品类加成 */
    private double calcContentMatch(List<Double> longVec, List<Double> shortVec, VideoContent vc) {
        if (vc == null || vc.getContentVector() == null) return RecommendationConfig.DEF_SCORE_MED;
        List<Double> videoVec = parseVector(vc.getContentVector());
        if (videoVec == null) return RecommendationConfig.DEF_SCORE_MED;

        double longSim = longVec != null ? cosineSim(longVec, videoVec) : 0.0;
        double shortSim = shortVec != null ? cosineSim(shortVec, videoVec) : longSim;

        return ScoringFunctions.clamp(longSim * RecommendationConfig.CM_LONG_W + shortSim * RecommendationConfig.CM_SHORT_W, 0, 1);
    }

    /**
     * 质量分: 互动率 + 内容质量 + 全站完播率 + 作者粉丝量。
     * 完播率高说明用户普遍看完该视频 → 质量好的强信号。
     */
    private double calcQualityScore(Video v, VideoContent vc, long followerCount, double avgCompletion) {
        double playCount = Math.max(1, v.getPlayCount() != null ? v.getPlayCount() : 1);
        long likes = v.getLikeCount() != null ? v.getLikeCount() : 0;
        long comments = v.getCommentCount() != null ? v.getCommentCount() : 0;
        long collects = v.getCollectCount() != null ? v.getCollectCount() : 0;
        long shares = v.getShareCount() != null ? v.getShareCount() : 0;

        // 互动率: 收藏和转发是更强的信号 (各计 2 倍权重)
        double interactions = likes + comments + collects * RecommendationConfig.QW_COLLECT_MULT + shares * RecommendationConfig.QW_SHARE_MULT;
        double engageRate = Math.min(1.0, interactions / playCount * RecommendationConfig.QW_ENGAGE_SCALE);

        double contentQuality = vc != null && vc.getQualityScore() != null ? vc.getQualityScore() : RecommendationConfig.DEF_QUALITY;

        // 作者粉丝量对数归一化
        double followerScore = 0.0;
        if (followerCount > 0) {
            followerScore = ScoringFunctions.logNormalize(followerCount, 1, RecommendationConfig.LOG_REF_FOLLOWER);
        }

        // 全站完播率: 大家普遍看完 = 内容质量好
        double completionScore = ScoringFunctions.clamp(avgCompletion, 0, 1);

        return engageRate * RecommendationConfig.QW_ENGAGE_RATE
                + contentQuality * RecommendationConfig.QW_CONTENT_QUAL
                + completionScore * RecommendationConfig.QW_COMPLETION
                + followerScore * RecommendationConfig.QW_FOLLOWER;
    }

    /** 创作者亲和力: 用户对该创作者的历史喜好度 */
    private double calcCreatorAffinity(Long authorId, Map<String, Double> affinity) {
        if (authorId == null || affinity.isEmpty()) return RecommendationConfig.CA_DEFAULT;
        return ScoringFunctions.clamp(affinity.getOrDefault(String.valueOf(authorId), 0.0),
                RecommendationConfig.CA_CLAMP_MIN, RecommendationConfig.CA_CLAMP_MAX);
    }

    /** 行为匹配: 搜索词匹配 (渐进) + 趋势关键词 + 品类偏好 + 时长偏好 */
    private double calcBehavioralMatch(Video v, VideoContent vc,
                                       List<String> recentSearches,
                                       Map<String, Double> catWeights,
                                       String userType,
                                       List<String> trendingKeywords) {
        double score = RecommendationConfig.DEF_SCORE_LOW;

        if (v.getDesc() != null) {
            String descLower = v.getDesc().toLowerCase();
            List<String> descWords = Arrays.asList(descLower.split("\\s+"));

            if (!recentSearches.isEmpty()) {
                for (String query : recentSearches) {
                    if (query == null) continue;
                    String qLower = query.toLowerCase();
                    if (descLower.contains(qLower)) {
                        score += RecommendationConfig.BEH_FULL_QUERY_MATCH;
                        break;
                    }
                    for (String qWord : qLower.split("\\s+")) {
                        if (qWord.length() >= RecommendationConfig.KEYWORD_MIN_LEN
                                && descLower.contains(qWord)) {
                            score += RecommendationConfig.BEH_PARTIAL_WORD_MATCH;
                        }
                    }
                }
            }

            if (!trendingKeywords.isEmpty()) {
                int trendHits = 0;
                for (String kw : trendingKeywords) {
                    if (descLower.contains(kw)) trendHits++;
                }
                score += Math.min(RecommendationConfig.BEH_TREND_CAP,
                        trendHits * RecommendationConfig.BEH_TREND_PER_HIT);
            }
        }

        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(),
                    RecommendationConfig.BEH_DEFAULT_CAT_W);
            score += catWeight * RecommendationConfig.BEH_CAT_W_MULT;
        }

        if (v.getDuration() != null) {
            double dur = v.getDuration();
            if (dur >= RecommendationConfig.BEH_DURATION_MIN
                    && dur <= RecommendationConfig.BEH_DURATION_MAX) {
                score += RecommendationConfig.BEH_DURATION_BONUS;
            }
        }

        return ScoringFunctions.clamp(score, 0, 1);
    }

    /** 社交分: 关注 1.0, 间接关系 0.3, 无关系 0.1 */
    private double calcSocialScore(Long userId, Video v,
                                    Map<Long, Double> authorCache, Set<Long> followed) {
        Long authorId = v.getAuthorUserId();
        if (authorId == null || authorId.equals(userId)) return RecommendationConfig.SOC_NONE;
        return authorCache.computeIfAbsent(authorId, aid -> {
            if (followed.contains(aid)) return RecommendationConfig.SOC_FOLLOWED;
            if (followed.isEmpty()) return RecommendationConfig.SOC_NONE;
            Long indirectCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowId, aid)
                    .in(Follow::getUserId, followed));
            if (indirectCount > 0) return RecommendationConfig.SOC_INDIRECT;
            return RecommendationConfig.SOC_NONE;
        });
    }

    /** 个人历史: 基于用户观看行为模式 */
    private double calcPersonalHistory(Long userId, Video v, VideoContent vc,
                                       UserContentProfile profile) {
        double score = RecommendationConfig.DEF_SCORE_LOW;

        double completionRate = profile.getAvgCompletionRate() != null
                ? profile.getAvgCompletionRate() : RecommendationConfig.DEF_COMPLETION;
        double repeatRate = profile.getRepeatViewRate() != null
                ? profile.getRepeatViewRate() : RecommendationConfig.DEF_SCORE_LOW;
        if (repeatRate > RecommendationConfig.DEF_SCORE_MED
                && completionRate > RecommendationConfig.VAL_HIGH_COMPLETION) {
            score += RecommendationConfig.PH_REPEAT_COMPLETION_BONUS;
        }

        Double prefMin = profile.getPreferredDurationMin();
        Double prefMax = profile.getPreferredDurationMax();
        if (prefMin != null && prefMax != null && v.getDuration() != null) {
            double dur = v.getDuration();
            if (dur >= prefMin && dur <= prefMax) score += RecommendationConfig.PH_DURATION_EXACT_BONUS;
            else if (dur >= prefMin * RecommendationConfig.PH_DURATION_MARGIN_FACTOR
                    && dur <= prefMax * (1 + RecommendationConfig.PH_DURATION_MARGIN_FACTOR)) {
                score += RecommendationConfig.PH_DURATION_MARGIN_BONUS;
            }
        }

        if (vc != null && vc.getMusicBpm() != null
                && profile.getPreferredBpmMin() != null && profile.getPreferredBpmMax() != null) {
            double bpm = vc.getMusicBpm();
            if (bpm >= profile.getPreferredBpmMin() && bpm <= profile.getPreferredBpmMax()) {
                score += RecommendationConfig.PH_BPM_EXACT_BONUS;
            }
        }

        return ScoringFunctions.clamp(score, 0, 1);
    }

    /** 热度趋势: 近期互动增长速度 */
    private double calcPopularityTrend(Video v) {
        if (v.getCreateTime() == null || v.getPlayCount() == null) return RecommendationConfig.DEF_SCORE_MED;
        long hoursSinceCreation = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        if (hoursSinceCreation <= 0) hoursSinceCreation = 1;

        double interactionsPerHour = (double)
                ((v.getLikeCount() != null ? v.getLikeCount() : 0)
                        + (v.getCommentCount() != null ? v.getCommentCount() : 0))
                / hoursSinceCreation;

        double trend = ScoringFunctions.sigmoid(interactionsPerHour, RecommendationConfig.SG_TREND_CENTER, RecommendationConfig.SG_TREND_STEEPNESS);
        return ScoringFunctions.clamp(trend, 0, 1);
    }

    /**
     * 新鲜度: 发布时间 + 近期互动活跃度。
     *
     * 不再是单纯的时间衰减！近期互动活跃的旧视频也会得到较高新鲜分。
     * - 新发布 + 高互动 → 1.0 (爆款新视频)
     * - 旧视频 + 近期翻红 (高pulse) → 0.6~0.8 (翻红视频不应被时间压死)
     * - 旧视频 + 彻底沉寂 → 指数衰减 (自然淘汰)
     * - 新发布 + 无人问津 → 0.3~0.5 (新但没人看)
     */
    private double calcFreshness(Video v, RecentEngagement re) {
        if (v.getCreateTime() == null) return RecommendationConfig.DEF_SCORE_HALF;
        long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
        double timeScore = ScoringFunctions.expDecay(hours, RecommendationConfig.HL_FRESHNESS);
        double pulse = re != null ? re.pulse() : RecommendationConfig.PULSE_INACTIVE;

        if (re != null && re.isActive()) {
            // 活跃度越高，时间衰减越弱
            return Math.max(RecommendationConfig.DEF_SCORE_HALF,
                    timeScore * (1 - RecommendationConfig.PULSE_LIKE_W) + pulse * RecommendationConfig.PULSE_LIKE_W);
        }

        // 无近期互动 → 正常时间衰减
        return timeScore;
    }

    /** 用户类型加成 */
    private double calcUserTypeBonus(String userType, Video v, VideoContent vc,
                                     UserContentProfile profile) {
        switch (userType) {
            case "passive_consumer":
                double quality = vc != null && vc.getQualityScore() != null
                        ? vc.getQualityScore() : RecommendationConfig.DEF_QUALITY;
                return quality * RecommendationConfig.UT_PASSIVE_CONSUMER_QUALITY_W
                        + RecommendationConfig.UT_PASSIVE_CONSUMER_BASE;
            case "social_butterfly":
                return RecommendationConfig.UT_SOCIAL_BUTTERFLY;
            case "power_liker":
                double engageRate = v.getLikeCount() != null && v.getPlayCount() != null
                        ? (double) v.getLikeCount() / Math.max(1, v.getPlayCount()) : 0;
                return ScoringFunctions.clamp(engageRate * RecommendationConfig.UT_POWER_LIKER_SCALE, 0, 1);
            case "collector":
                if (vc != null && vc.getTextCategory() != null) {
                    String cat = vc.getTextCategory();
                    if (cat.contains("知识") || cat.contains("教程") || cat.contains("美食"))
                        return RecommendationConfig.UT_COLLECTOR_MATCH;
                }
                return RecommendationConfig.UT_COLLECTOR_DEFAULT;
            case "active_searcher":
                return RecommendationConfig.UT_ACTIVE_SEARCHER;
            case "creator_fan":
                String affStr = profile.getCreatorAffinity();
                if (affStr != null && v.getAuthorUserId() != null
                        && affStr.contains(String.valueOf(v.getAuthorUserId()))) {
                    return RecommendationConfig.UT_CREATOR_FAN_MATCH;
                }
                return RecommendationConfig.UT_CREATOR_FAN_DEFAULT;
            case "explorer":
                return RecommendationConfig.UT_EXPLORER;
            case "new_user":
                return RecommendationConfig.UT_NEW_USER;
            default:
                return RecommendationConfig.UT_BALANCED;
        }
    }

    /** 探索激励: 给用户未充分接触的品类/新作者适当加权 */
    private double calcExplorationBonus(Video v, VideoContent vc,
                                        Map<String, Double> catWeights,
                                        UserContentProfile profile) {
        double bonus = 0.0;

        if (vc != null && vc.getTextCategory() != null) {
            double catWeight = catWeights.getOrDefault(vc.getTextCategory(), 0.0);
            bonus += (1.0 - catWeight) * RecommendationConfig.EXPLORE_CAT_MULT;
        }

        String affStr = profile.getCreatorAffinity();
        if (affStr == null || !affStr.contains(String.valueOf(v.getAuthorUserId()))) {
            bonus += RecommendationConfig.EXPLORE_NEW_AUTHOR;
        }

        if (vc != null && vc.getQualityScore() != null
                && vc.getQualityScore() > RecommendationConfig.EXPLORE_QUAL_THRESHOLD) {
            bonus += RecommendationConfig.EXPLORE_HIGH_QUAL;
        }

        return ScoringFunctions.clamp(bonus, 0, 1);
    }

    // ===================== 新增信号 =====================

    /**
     * 观看历史惩罚: 用户已经看过这个视频？降权！
     *
     * 惩罚分级:
     * - 完整看完但未互动(点赞/收藏) → 强烈不感兴趣，惩罚 0.8~1.0
     * - 看了一部分但未互动 → 可能不感兴趣，惩罚 0.4~0.6
     * - 快速划走 (swipeSeconds < 3s) → 明显不感兴趣，惩罚 0.6~0.8
     * - 短时观看但用户整体 bounceRate 高 → 可能是正常浏览，惩罚 0.2
     * - 从未看过 → 惩罚 0
     */
    private double calcWatchHistoryPenalty(WatchHistory wh, Video v,
                                           UserContentProfile profile) {
        if (wh == null) return 0.0; // 从未看过，无惩罚

        double watchDuration = wh.getWatchDuration() != null ? wh.getWatchDuration() : 0;
        double videoDuration = wh.getVideoDuration() != null && wh.getVideoDuration() > 0
                ? wh.getVideoDuration() : v.getDuration() != null ? v.getDuration() : RecommendationConfig.DEFAULT_VIDEO_DURATION_SEC;
        boolean finished = wh.getFinished() != null && wh.getFinished() == 1;
        double swipeSeconds = wh.getSwipeSeconds() != null ? wh.getSwipeSeconds() : watchDuration;

        if (finished) {
            double repeatRate = profile.getRepeatViewRate() != null
                    ? profile.getRepeatViewRate() : RecommendationConfig.DEF_SCORE_LOW;
            return ScoringFunctions.clamp(
                    RecommendationConfig.PEN_FINISHED_BASE - repeatRate * RecommendationConfig.PEN_FINISHED_REPEAT_FACTOR,
                    RecommendationConfig.PEN_FINISHED_FLOOR, RecommendationConfig.PEN_FINISHED_CEIL);
        }

        if (swipeSeconds < RecommendationConfig.UP_BOUNCE_THRESHOLD_SEC) {
            return RecommendationConfig.PEN_FAST_SWIPE;
        }

        double completion = watchDuration / Math.max(1, videoDuration);
        if (completion > RecommendationConfig.DEF_SCORE_HALF) {
            return RecommendationConfig.PEN_HALF_WATCH;
        }

        double bounceRate = profile.getBounceRate() != null
                ? profile.getBounceRate() : RecommendationConfig.DEF_SCORE_MED;
        if (completion < RecommendationConfig.DEF_SCORE_MED && bounceRate > RecommendationConfig.DEF_SCORE_HALF) {
            return RecommendationConfig.PEN_HABITUAL;
        }

        return RecommendationConfig.PEN_SMALL_PORTION;
    }

    /**
     * 社会证明: 综合博主影响力 + 视频社交信号 + 动态时效因子。
     *
     * 关键: 不再用写死的时间衰减！改用近期互动活跃度动态判断:
     * - 近期互动活跃 (recent pulse 高) → 不衰减，视频正在翻红或持续火爆
     * - 近期互动沉寂 (recent pulse 低) → 逐步衰减，让位给新鲜内容
     *
     * 场景举例:
     * - 10年前100万赞 + 近期每天仍有赞 → 翻红中，不衰减 → 高分
     * - 10年前100万赞 + 近期无人问津 → 沉寂 → 衰减 → 不再压制新视频
     * - 昨天5万赞 → 新视频自然高分
     */
    private double calcSocialProof(Video v, long followerCount, RecentEngagement re) {
        long likes = v.getLikeCount() != null ? v.getLikeCount() : 0;
        long collects = v.getCollectCount() != null ? v.getCollectCount() : 0;
        long shares = v.getShareCount() != null ? v.getShareCount() : 0;
        long plays = v.getPlayCount() != null ? v.getPlayCount() : 1;

        double followerScore = ScoringFunctions.logNormalize(followerCount, 1, RecommendationConfig.LOG_REF_FOLLOWER);
        double likeScore = ScoringFunctions.logNormalize(likes, 1, RecommendationConfig.LOG_REF_LIKES);
        double collectScore = ScoringFunctions.logNormalize(collects, 1, RecommendationConfig.LOG_REF_COLLECT);
        double shareScore = ScoringFunctions.logNormalize(shares, 1, RecommendationConfig.LOG_REF_SHARE);

        double totalInteractions = likes + collects * RecommendationConfig.SPW_COLLECT_MULT + shares * RecommendationConfig.SPW_SHARE_MULT;
        double interactionRate = Math.min(1.0, totalInteractions / Math.max(1, plays) * RecommendationConfig.SPW_INTERACT_SCALE);

        double rawProof = followerScore * RecommendationConfig.SPW_FOLLOWER
                + likeScore * RecommendationConfig.SPW_LIKE
                + collectScore * RecommendationConfig.SPW_COLLECT
                + shareScore * RecommendationConfig.SPW_SHARE
                + interactionRate * RecommendationConfig.SPW_INTERACT_RATE;

        if (v.getCreateTime() != null) {
            double pulse = re != null ? re.pulse() : RecommendationConfig.PULSE_INACTIVE;
            if (pulse < RecommendationConfig.PULSE_INACTIVE) {
                // 近期基本无互动 → 按年龄衰减 (30天半衰期)
                long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
                double timeDecay = ScoringFunctions.expDecay(hours, RecommendationConfig.HL_SOCIAL_PROOF);
                rawProof *= timeDecay;
            }
            // pulse >= 0.02: 视频仍有近期互动 → 不衰减，保留完整社交证明
        }

        return rawProof;
    }

    /**
     * 创作者互动信号: 该视频的作者是不是我关注/点赞/收藏过的？
     *
     * 分层加分:
     * - 关注 + 互动过 (双重关系) → 0.9~1.0，该博主的新视频应优先推送
     * - 仅关注过 (未互动) → 0.7，优先推但略低于互动过的
     * - 仅互动过 (未关注) → 0.8，点赞/收藏说明喜欢该博主内容，值得推
     * - 新视频 (7天内) 额外加成 × 1.2，互动作者新视频胜出
     * - 无关系 → 0.0
     */
    private double calcCreatorInteraction(Video v, Set<Long> followedSet,
                                           Set<Long> interactedAuthorIds) {
        Long authorId = v.getAuthorUserId();
        if (authorId == null) return 0.0;

        boolean followed = followedSet.contains(authorId);
        boolean interacted = interactedAuthorIds.contains(authorId);

        if (!followed && !interacted) return 0.0;

        double baseScore;
        if (followed && interacted) {
            baseScore = RecommendationConfig.CI_DOUBLE_RELATION;
        } else if (interacted) {
            baseScore = RecommendationConfig.CI_INTERACTED_ONLY;
        } else {
            baseScore = RecommendationConfig.CI_FOLLOWED_ONLY;
        }

        if (v.getCreateTime() != null && interacted) {
            long hours = ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now());
            if (hours <= RecommendationConfig.CI_RECENCY_HOURS) {
                double recencyBoost = 1.0 + Math.max(0,
                        (RecommendationConfig.CI_RECENCY_HOURS - hours)
                                / (double) RecommendationConfig.CI_RECENCY_HOURS)
                        * RecommendationConfig.CI_RECENCY_BOOST;
                baseScore *= recencyBoost;
            }
        }

        return ScoringFunctions.clamp(baseScore, 0, 1);
    }

    // ===================== 多样性重排 =====================

    private List<Long> reRank(List<ScoredVideo> scored, Map<Long, VideoContent> contentMap,
                               List<Long> exploreIds, int pageSize,
                               Map<Long, Long> videoAgeHours,
                               Map<Long, String> videoTypeMap) {
        Set<Long> exploreSet = new HashSet<>(exploreIds);
        List<ScoredVideo> mainPool = new ArrayList<>();
        List<ScoredVideo> explorePool = new ArrayList<>();

        for (ScoredVideo sv : scored) {
            if (exploreSet.contains(sv.videoId)) {
                explorePool.add(sv);
            } else {
                mainPool.add(sv);
            }
        }

        List<Long> result = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        Map<String, Integer> catCount = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        int recentCount = 0;
        int exploreEvery = Math.max(RecommendationConfig.DIVERSITY_EXPLORE_MIN,
                (int) Math.round(1.0 / Math.max(0.0001, RecommendationConfig.EXPLORE_INJECT_RATE)));

        while (result.size() < pageSize) {
            boolean injectExplore = !explorePool.isEmpty()
                    && (result.size() + 1) % exploreEvery == 0;
            // 时间多样性: 已有半数以上是48h内视频 → 跳过新的优先选老的
            boolean needOlder = recentCount > result.size() / 2 && result.size() > 2;

            ScoredVideo picked = null;
            if (injectExplore) {
                picked = removeBestCandidate(explorePool, false, authorCount, catCount,
                        typeCount, videoAgeHours, videoTypeMap);
            }
            if (picked == null && !mainPool.isEmpty()) {
                picked = removeBestCandidate(mainPool, needOlder, authorCount, catCount,
                        typeCount, videoAgeHours, videoTypeMap);
            }
            if (picked == null && !explorePool.isEmpty()) {
                picked = removeBestCandidate(explorePool, needOlder, authorCount, catCount,
                        typeCount, videoAgeHours, videoTypeMap);
            }
            if (picked == null) break;

            if (picked != null && !result.contains(picked.videoId)) {
                result.add(picked.videoId);
                authorCount.merge(picked.authorId, 1, Integer::sum);
                if (picked.category != null) {
                    catCount.merge(picked.category, 1, Integer::sum);
                }
                String vtype = videoTypeMap.getOrDefault(picked.videoId, "unknown");
                typeCount.merge(vtype, 1, Integer::sum);
                if (videoAgeHours.getOrDefault(picked.videoId, 0L)
                        < RecommendationConfig.DIVERSITY_RECENT_HOURS) recentCount++;
                picked = null;
            }
        }

        return result;
    }

    /**
     * Pick within a bounded look-ahead window and remove only the chosen item.
     * Diversity is a soft re-ranking constraint: skipped high-score candidates
     * remain available for later slots instead of disappearing from the pool.
     */
    private ScoredVideo removeBestCandidate(List<ScoredVideo> pool, boolean preferOlder,
                                             Map<Long, Integer> authorCount,
                                             Map<String, Integer> catCount,
                                             Map<String, Integer> typeCount,
                                             Map<Long, Long> videoAgeHours,
                                             Map<Long, String> videoTypeMap) {
        if (pool.isEmpty()) return null;
        int scanLimit = Math.min(pool.size(), RecommendationConfig.DIVERSITY_MAX_SKIP + 1);
        int firstEligible = -1;
        for (int i = 0; i < scanLimit; i++) {
            ScoredVideo candidate = pool.get(i);
            if (violatesDiversity(candidate, authorCount, catCount, typeCount, videoTypeMap)) {
                continue;
            }
            if (firstEligible < 0) firstEligible = i;
            boolean recent = videoAgeHours.getOrDefault(candidate.videoId, 0L)
                    < RecommendationConfig.DIVERSITY_RECENT_HOURS;
            if (!preferOlder || !recent) {
                return pool.remove(i);
            }
            if (i >= RecommendationConfig.DIVERSITY_TIME_SKIP) break;
        }

        // If every nearby candidate hits a cap, relax the cap for the best
        // remaining score. This preserves page fill and keeps the choice
        // deterministic without inventing a user-specific shuffle.
        return pool.remove(firstEligible >= 0 ? firstEligible : 0);
    }

    private boolean violatesDiversity(ScoredVideo sv, Map<Long, Integer> authorCount,
                                       Map<String, Integer> catCount,
                                       Map<String, Integer> typeCount,
                                       Map<Long, String> videoTypeMap) {
        if (authorCount.getOrDefault(sv.authorId, 0) >= RecommendationConfig.MAX_PER_AUTHOR) return true;
        if (sv.category != null && catCount.getOrDefault(sv.category, 0) >= RecommendationConfig.MAX_PER_CATEGORY) return true;
        String vtype = videoTypeMap.getOrDefault(sv.videoId, "unknown");
        if (typeCount.getOrDefault(vtype, 0) >= RecommendationConfig.MAX_PER_TYPE) return true;
        return false;
    }

    // ===================== 曝光管理 =====================

    private Set<Long> getRecentExposures(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(RecommendationConfig.DIVERSITY_RECENT_HOURS);
        return new HashSet<>(exposureMapper.findRecentVideoIds(
                userId, since, MAX_EXPOSURE_EXCLUDES));
    }

    private void recordExposuresBatch(Long userId, List<Long> videoIds) {
        LocalDateTime now = LocalDateTime.now();
        for (Long vid : videoIds) {
            VideoExposure e = new VideoExposure();
            e.setUserId(userId);
            e.setVideoId(vid);
            e.setExposureTime(now);
            e.setSwipeSeconds(0.0);
            e.setClicked(0);
            try { exposureMapper.insert(e); } catch (Exception ignored) {}
        }
    }

    /** Record only IDs actually returned to a viewer. */
    public void recordExposures(Long userId, List<Long> videoIds) {
        if (userId == null || videoIds == null || videoIds.isEmpty()) return;
        recordExposuresBatch(userId, videoIds);
    }

    // ===================== 工具方法 =====================

    private Map<Long, VideoContent> loadContentMap(List<Long> videoIds) {
        if (videoIds.isEmpty()) return Map.of();
        return contentMapper.selectBatchIds(videoIds).stream()
                .collect(Collectors.toMap(VideoContent::getVideoId, vc -> vc, (a, b) -> a));
    }

    private Set<Long> getFollowedSet(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId))
                .stream().map(Follow::getFollowId).collect(Collectors.toSet());
    }

    private List<Double> parseVector(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** Prefer the fused vector, then long-term, then short-term interest. */
    private String profileVector(UserContentProfile profile) {
        if (profile == null) return null;
        if (profile.getContentVector() != null && !profile.getContentVector().isBlank()) {
            return profile.getContentVector();
        }
        if (profile.getLongTermVector() != null && !profile.getLongTermVector().isBlank()) {
            return profile.getLongTermVector();
        }
        return profile.getShortTermVector();
    }

    private double cosineSim(List<Double> a, VideoContent vc) {
        if (a == null || vc == null || vc.getContentVector() == null) return 0.0;
        List<Double> b = parseVector(vc.getContentVector());
        return cosineSim(a, b);
    }

    private double cosineSim(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double categoryBonus(UserContentProfile profile, VideoContent vc) {
        if (profile == null || vc == null || vc.getTextCategory() == null) return 0;
        Map<String, Double> weights = parseCategoryWeights(profile.getCategoryWeights());
        return weights.getOrDefault(vc.getTextCategory(), 0.0) * RecommendationConfig.TM_CATEGORY_CROSS_MULT;
    }

    private Map<String, Double> parseCategoryWeights(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Double> parseCreatorAffinityMap(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) { return Map.of(); }
    }

    private List<String> parseRecentSearches(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) { return List.of(); }
    }

    // ===================== 内部类 =====================

    private record CandidateScore(Long videoId, double score) {}

    private record CategoryFeedback(Map<String, Double> skipPenalties, Map<String, Double> likeBoosts) {}

    /** 打分后的候选视频 (13 维信号) */
    public record ScoredVideo(
            Long videoId,
            Long authorId,
            String category,
            double score,
            double contentMatch,
            double qualityScore,
            double creatorAffinity,
            double behavioralMatch,
            double socialScore,
            double personalHistory,
            double popularityTrend,
            double freshness,
            double userTypeBonus,
            double explorationBonus,
            double watchHistoryPenalty,
            double socialProof,
            double creatorInteraction
    ) {}
}

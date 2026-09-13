package com.douyin.service;

import com.douyin.entity.UserContentProfile;
import com.douyin.entity.Video;
import com.douyin.entity.VideoContent;
import com.douyin.entity.VideoExposure;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserContentProfileMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recommendation contracts that should remain true while the ranker evolves:
 * measured signals influence HOME, identical evidence is not artificially
 * shuffled by account ID, and HOT does not read personalized profile state.
 */
class RecommendationEnginePersonalizationTest {

    private VideoMapper videos;
    private VideoContentMapper contents;
    private VideoExposureMapper exposures;
    private UserContentProfileMapper profiles;
    private LikeMapper likes;
    private FollowMapper follows;
    private WatchHistoryMapper history;
    private VideoCollectMapper collects;
    private UserMapper users;
    private UserProfileService profileService;
    private RecommendationEngine engine;
    private List<Video> pool;
    private Map<Long, VideoContent> contentByVideo;

    @BeforeEach
    void setUp() {
        videos = mock(VideoMapper.class);
        contents = mock(VideoContentMapper.class);
        exposures = mock(VideoExposureMapper.class);
        profiles = mock(UserContentProfileMapper.class);
        likes = mock(LikeMapper.class);
        follows = mock(FollowMapper.class);
        history = mock(WatchHistoryMapper.class);
        collects = mock(VideoCollectMapper.class);
        users = mock(UserMapper.class);
        profileService = mock(UserProfileService.class);

        pool = new ArrayList<>();
        contentByVideo = new java.util.HashMap<>();
        LocalDateTime created = LocalDateTime.now().minusHours(3);
        for (long id = 1; id <= 80; id++) {
            Video video = new Video();
            video.setId(id);
            video.setAuthorUserId(10_000L + id);
            video.setType("recommend-video");
            video.setStatus("APPROVED");
            video.setIsDelete(0);
            video.setDuration(30.0);
            video.setCreateTime(created.minusMinutes(id));
            video.setLikeCount(0L);
            video.setCommentCount(0L);
            video.setCollectCount(0L);
            video.setShareCount(0L);
            video.setPlayCount(1L);
            pool.add(video);

            VideoContent content = new VideoContent();
            content.setVideoId(id);
            content.setTextCategory(id % 2 == 0 ? "travel" : "food");
            content.setContentVector(id % 2 == 0 ? "[0.0,1.0]" : "[1.0,0.0]");
            content.setQualityScore(0.5);
            content.setExtractStatus(1);
            contentByVideo.put(id, content);
        }

        when(exposures.findRecentVideoIds(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(likes.findRecentLikes(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(collects.findRecentCollects(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(history.findRecentFinishedVideoIds(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(history.findRecentCategoryFeedback(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(likes.findRecentLikedVideoIds(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(follows.selectList(any())).thenReturn(List.of());
        when(videos.findRecentAuthorIds(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(videos.selectList(any())).thenReturn(List.of());
        when(contents.selectList(any())).thenReturn(List.of());
        when(profiles.selectList(any())).thenReturn(List.of());
        when(history.selectList(any())).thenReturn(List.of());
        when(likes.countRecentLikes(any(), any())).thenReturn(List.of());
        when(history.countRecentWatches(any(), any())).thenReturn(List.of());
        when(history.avgCompletionRate(any())).thenReturn(List.of());
        when(users.selectBatchIds(any())).thenReturn(List.of());
        when(exposures.insert(org.mockito.ArgumentMatchers.<VideoExposure>any())).thenReturn(1);
        when(profileService.getOrCreate(any())).thenAnswer(invocation ->
                profile(invocation.getArgument(0), null));

        when(videos.findRecallCandidates(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            Collection<Long> excluded = invocation.getArgument(0);
            Double minDuration = invocation.getArgument(1);
            return pool.stream()
                    .filter(v -> !excluded.contains(v.getId()))
                    .filter(v -> minDuration == null || v.getDuration() >= minDuration)
                    .toList();
        });
        when(videos.selectBatchIds(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            Set<Long> wanted = ids.stream().map(id -> ((Number) id).longValue()).collect(Collectors.toSet());
            return pool.stream().filter(v -> wanted.contains(v.getId())).toList();
        });
        when(contents.selectBatchIds(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            Set<Long> wanted = ids.stream().map(id -> ((Number) id).longValue()).collect(Collectors.toSet());
            return wanted.stream().map(contentByVideo::get).toList();
        });

        engine = new RecommendationEngine(videos, contents, exposures, profiles, likes, follows,
                history, collects, users, profileService);
    }

    @Test
    void homePoolAndOrderReflectDifferentAccountProfiles() {
        UserContentProfile food = profile(101L, "[1.0,0.0]");
        UserContentProfile travel = profile(202L, "[0.0,1.0]");
        when(profiles.selectById(101L)).thenReturn(food);
        when(profiles.selectById(202L)).thenReturn(travel);

        List<Long> accountA = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> accountB = engine.recommend(202L, 12, null, FeedChannel.HOME);

        assertEquals(12, accountA.size());
        assertEquals(12, accountB.size());
        assertNotEquals(accountA, accountB,
                "different profile vectors must not collapse to one global ordering");
    }

    @Test
    void accountsWithoutPersonalizationEvidenceAreNotArtificiallyShuffled() {
        when(profiles.selectById(101L)).thenReturn(null);
        when(profiles.selectById(202L)).thenReturn(null);

        List<Long> accountA = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> accountB = engine.recommend(202L, 12, null, FeedChannel.HOME);

        assertEquals(accountA, accountB,
                "account ID alone must not manufacture a different recommendation order");
    }

    @Test
    void accountsWithIdenticalProfilesAreNotArtificiallyShuffled() {
        when(profiles.selectById(101L)).thenReturn(profile(101L, "[1.0,0.0]"));
        when(profiles.selectById(202L)).thenReturn(profile(202L, "[1.0,0.0]"));

        List<Long> accountA = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> accountB = engine.recommend(202L, 12, null, FeedChannel.HOME);

        assertEquals(accountA, accountB,
                "equal profile and behavior evidence must produce the same deterministic order");
    }

    @Test
    void homeRetryIsStableForTheSameEvidence() {
        when(profiles.selectById(101L)).thenReturn(profile(101L, "[1.0,0.0]"));

        List<Long> first = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> retry = engine.recommend(101L, 12, null, FeedChannel.HOME);

        assertEquals(first, retry,
                "a retry must not reshuffle the evidence-based exploration pool");
    }

    @Test
    void diversityRerankingDefersCandidatesInsteadOfDroppingThem() {
        UserContentProfile established = profile(101L, "[1.0,0.0]");
        established.setTotalViewCount(100);
        established.setTotalWatchCount(100);
        when(profiles.selectById(101L)).thenReturn(established);

        List<Long> ranked = engine.recommendPool(101L, pool.size(), null, FeedChannel.HOME);

        assertEquals(pool.size(), ranked.size(),
                "soft diversity caps must not shrink a healthy candidate pool");
        assertEquals(ranked.size(), Set.copyOf(ranked).size(),
                "deferred candidates must still be emitted exactly once");
    }

    @Test
    void explorationUsesAStableCadenceWithoutAccountSeededShuffling() {
        UserContentProfile established = profile(101L, "[1.0,0.0]");
        established.setTotalViewCount(100);
        established.setTotalWatchCount(100);
        established.setCategoryWeights("{\"food\":1.0,\"travel\":0.0}");
        when(profiles.selectById(101L)).thenReturn(established);

        List<Long> first = engine.recommendPool(101L, 12, null, FeedChannel.HOME);
        List<Long> retry = engine.recommendPool(101L, 12, null, FeedChannel.HOME);

        assertEquals(first, retry);
        assertEquals(12, first.size());
        assertTrue(first.subList(0, 7).stream().allMatch(id -> id % 2 == 1),
                "the main relevance pool should lead the page");
        assertTrue(first.get(7) % 2 == 0,
                "the eighth slot should deterministically admit an exploration candidate");
    }

    @Test
    void realExposureEvidenceChangesCandidatesWithoutAccountIdShuffling() {
        UserContentProfile sameEvidenceA = profile(101L, "[1.0,0.0]");
        UserContentProfile sameEvidenceB = profile(202L, "[1.0,0.0]");
        when(profiles.selectById(101L)).thenReturn(sameEvidenceA);
        when(profiles.selectById(202L)).thenReturn(sameEvidenceB);
        when(exposures.findRecentVideoIds(eq(101L), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(59L));

        List<Long> accountA = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> accountB = engine.recommend(202L, 12, null, FeedChannel.HOME);

        assertNotEquals(accountA, accountB,
                "a real exposure signal may change the candidate set");
        assertTrue(!accountA.contains(59L),
                "recently exposed content must be excluded for that account");
    }

    @Test
    void exhaustedExposureWindowRecyclesContentBeforeGlobalFallback() {
        UserContentProfile food = profile(101L, "[1.0,0.0]");
        UserContentProfile travel = profile(202L, "[0.0,1.0]");
        when(profiles.selectById(101L)).thenReturn(food);
        when(profiles.selectById(202L)).thenReturn(travel);
        when(exposures.findRecentVideoIds(eq(101L), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(pool.stream().map(Video::getId).toList());
        when(exposures.findRecentVideoIds(eq(202L), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(pool.stream().map(Video::getId).toList());

        List<Long> accountA = engine.recommend(101L, 12, null, FeedChannel.HOME);
        List<Long> accountB = engine.recommend(202L, 12, null, FeedChannel.HOME);

        assertEquals(12, accountA.size());
        assertEquals(12, accountB.size());
        assertNotEquals(accountA, accountB,
                "an exhausted exposure window must recycle candidates through the ranker, not erase profile evidence via global HOT fallback");
    }

    @Test
    void partiallyExhaustedExposureWindowFillsPageFromUnfinishedRecycledContent() {
        UserContentProfile food = profile(101L, "[1.0,0.0]");
        when(profiles.selectById(101L)).thenReturn(food);
        when(exposures.findRecentVideoIds(eq(101L), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(pool.subList(0, 79).stream().map(Video::getId).toList());

        List<Long> ranked = engine.recommend(101L, 12, null, FeedChannel.HOME);

        assertEquals(12, ranked.size(),
                "a nearly exhausted exposure window should recycle unfinished items to keep the requested page full");
        assertTrue(ranked.contains(80L),
                "the one unseen candidate remains eligible while recycling fills the rest of the page");
    }

    @Test
    void hotModeIsGlobalAndDoesNotLoadUserProfile() {
        when(videos.selectList(any())).thenReturn(pool.subList(0, 12));

        List<Long> first = engine.recommend(101L, 12, null, FeedChannel.HOT);
        List<Long> second = engine.recommend(202L, 12, null, FeedChannel.HOT);

        assertEquals(first, second);
        verify(profiles, never()).selectById(any());
    }

    private static UserContentProfile profile(Long userId, String vector) {
        UserContentProfile profile = new UserContentProfile();
        profile.setUserId(userId);
        profile.setContentVector(vector);
        profile.setTotalViewCount(20);
        profile.setTotalWatchCount(20);
        profile.setUserType("balanced");
        profile.setUserSegment("medium");
        profile.setAvgCompletionRate(0.5);
        profile.setBounceRate(0.1);
        return profile;
    }
}

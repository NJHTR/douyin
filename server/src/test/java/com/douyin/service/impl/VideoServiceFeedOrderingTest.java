package com.douyin.service.impl;

import com.douyin.entity.Video;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.RecommendationEngine;
import com.douyin.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoServiceFeedOrderingTest {

    private VideoMapper videos;
    private VideoServiceImpl service;

    @BeforeEach
    void setUp() {
        videos = mock(VideoMapper.class);
        LikeMapper likes = mock(LikeMapper.class);
        FollowMapper follows = mock(FollowMapper.class);
        VideoCollectMapper collects = mock(VideoCollectMapper.class);
        UserMapper users = mock(UserMapper.class);
        when(users.selectBatchIds(any())).thenReturn(List.of());
        when(likes.selectList(any())).thenReturn(List.of());
        when(follows.selectList(any())).thenReturn(List.of());
        when(collects.selectList(any())).thenReturn(List.of());

        service = new VideoServiceImpl(
                users,
                likes,
                follows,
                collects,
                mock(WatchHistoryMapper.class),
                mock(RecommendationEngine.class),
                mock(ContentFeatureService.class),
                mock(VideoContentMapper.class),
                mock(SearchService.class));
        ReflectionTestUtils.setField(service, "baseMapper", videos);
    }

    @Test
    void equalHotScoresUseGlobalVideoIdTieBreakRegardlessOfQueryOrder() {
        Video one = video(1L);
        Video two = video(2L);
        Video three = video(3L);
        when(videos.selectList(any()))
                .thenReturn(List.of(one, three, two))
                .thenReturn(List.of(two, one, three));

        List<Long> first = service.getTrendingVideos(101L, 1, 3).getList().stream()
                .map(item -> item.getAwemeId())
                .toList();
        List<Long> second = service.getTrendingVideos(202L, 1, 3).getList().stream()
                .map(item -> item.getAwemeId())
                .toList();

        assertEquals(List.of(3L, 2L, 1L), first);
        assertEquals(first, second,
                "account identity and database row order must not break equal-score ties");
    }

    @Test
    void localRecommendationPoolHasAHardMemoryBound() throws Exception {
        Method cache = VideoServiceImpl.class.getDeclaredMethod(
                "cacheLocalRecommendationPool", String.class, long.class, List.class);
        cache.setAccessible(true);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 1_025; i++) {
            cache.invoke(service, "user:" + i, now + i, List.of((long) i));
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> pools = (Map<String, ?>) ReflectionTestUtils.getField(
                service, "localRecommendationPools");
        assertEquals(1_000, pools.size(),
                "the per-JVM recommendation acceleration cache must not grow with sessions");
        org.junit.jupiter.api.Assertions.assertFalse(pools.containsKey("user:0"),
                "the oldest active session pool should be evicted first");
    }

    private static Video video(Long id) {
        Video video = new Video();
        video.setId(id);
        video.setAuthorUserId(10_000L + id);
        video.setType("recommend-video");
        video.setStatus("APPROVED");
        video.setCreateTime(LocalDateTime.of(2026, 8, 1, 12, 0));
        video.setLikeCount(10L);
        video.setCollectCount(0L);
        video.setShareCount(0L);
        video.setCommentCount(0L);
        video.setPlayCount(100L);
        video.setDuration(30.0);
        return video;
    }
}

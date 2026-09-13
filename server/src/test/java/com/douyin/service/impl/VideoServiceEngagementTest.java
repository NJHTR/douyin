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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the service's affected-row protocol.  The database unique keys and
 * SQL increments provide the actual serialization; these tests make sure the
 * service does not regress to a read-modify-write implementation.
 */
class VideoServiceEngagementTest {

    private VideoMapper videos;
    private LikeMapper likes;
    private VideoCollectMapper collects;
    private UserMapper users;
    private VideoServiceImpl service;
    private Video video;

    @BeforeEach
    void setUp() {
        users = mock(UserMapper.class);
        likes = mock(LikeMapper.class);
        collects = mock(VideoCollectMapper.class);
        videos = mock(VideoMapper.class);

        service = new VideoServiceImpl(
                users,
                likes,
                mock(FollowMapper.class),
                collects,
                mock(WatchHistoryMapper.class),
                mock(RecommendationEngine.class),
                mock(ContentFeatureService.class),
                mock(VideoContentMapper.class),
                mock(SearchService.class));
        ReflectionTestUtils.setField(service, "baseMapper", videos);

        video = new Video();
        video.setId(42L);
        video.setAuthorUserId(7L);
        video.setLikeCount(10L);
        video.setCollectCount(3L);
        video.setShareCount(5L);
        when(videos.selectById(42L)).thenReturn(video);
    }

    @Test
    void firstLikeUsesInsertAffectedRowsAndAtomicCounters() {
        when(likes.insertIgnore(11L, 42L)).thenReturn(1);
        when(videos.incrementLike(42L, 1)).thenReturn(1);

        assertTrue(service.toggleLike(11L, 42L));

        verify(likes).insertIgnore(11L, 42L);
        verify(videos).incrementLike(42L, 1);
        verify(users).incrementTotalFavorited(7L, 1);
        verify(likes, never()).selectOne(any());
    }

    @Test
    void duplicateLikeDeletesRelationAndDecrementsOnlyOnce() {
        when(likes.insertIgnore(11L, 42L)).thenReturn(0);
        when(likes.deleteByUserAndVideo(11L, 42L)).thenReturn(1);
        when(videos.incrementLike(42L, -1)).thenReturn(1);

        assertFalse(service.toggleLike(11L, 42L));

        verify(videos).incrementLike(42L, -1);
        verify(users).incrementTotalFavorited(7L, -1);
    }

    @Test
    void concurrentRemovalRaceDoesNotTouchCountersWhenDeleteMisses() {
        when(likes.insertIgnore(11L, 42L)).thenReturn(0);
        when(likes.deleteByUserAndVideo(11L, 42L)).thenReturn(0);
        when(likes.selectCount(any())).thenReturn(1L);

        assertTrue(service.toggleLike(11L, 42L));

        verify(videos, never()).incrementLike(eq(42L), anyInt());
        verify(users, never()).incrementTotalFavorited(eq(7L), anyInt());
    }

    @Test
    void firstCollectUsesRelationInsertAndAtomicCounter() {
        when(collects.insertIgnore(11L, 42L)).thenReturn(1);
        when(videos.incrementCollect(42L, 1)).thenReturn(1);

        assertTrue(service.toggleCollect(11L, 42L));

        verify(collects).insertIgnore(11L, 42L);
        verify(videos).incrementCollect(42L, 1);
        verify(collects, never()).selectOne(any());
    }

    @Test
    void shareUsesAtomicIncrementAndReadsCurrentCount() {
        when(videos.incrementShare(42L, 1)).thenReturn(1);
        when(videos.selectShareCount(42L)).thenReturn(6L);

        assertEquals(6L, service.recordShare(42L));

        verify(videos).incrementShare(42L, 1);
        verify(videos).selectShareCount(42L);
        verify(videos, never()).updateById(any(Video.class));
    }
}

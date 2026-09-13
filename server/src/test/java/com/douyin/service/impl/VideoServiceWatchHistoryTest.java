package com.douyin.service.impl;

import com.douyin.entity.WatchHistory;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.RecommendationEngine;
import com.douyin.service.SearchService;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * P1-02 contract tests: watch reporting must be write-once at the service
 * boundary. The database upsert owns serialization for concurrent heartbeats.
 */
class VideoServiceWatchHistoryTest {

    private WatchHistoryMapper watchHistory;
    private VideoServiceImpl service;

    @BeforeEach
    void setUp() {
        watchHistory = mock(WatchHistoryMapper.class);
        service = new VideoServiceImpl(
                mock(UserMapper.class),
                mock(LikeMapper.class),
                mock(FollowMapper.class),
                mock(VideoCollectMapper.class),
                watchHistory,
                mock(RecommendationEngine.class),
                mock(ContentFeatureService.class),
                mock(VideoContentMapper.class),
                mock(SearchService.class));
    }

    @Test
    void recordWatchUsesSingleUpsertWithoutReadBeforeWrite() {
        service.recordWatch(11L, 42L, 7L, 12.5, 30, true,
                "HOME_RECOMMEND", "session-a", 4.0, 12.5);

        verify(watchHistory).upsertProgress(11L, 42L, 7L, 12.5, 30.0, 1,
                "HOME_RECOMMEND", "session-a", 4.0, 12.5);
        verify(watchHistory, never()).selectOne(org.mockito.ArgumentMatchers.any());
        verify(watchHistory, never()).insert(org.mockito.ArgumentMatchers.any(WatchHistory.class));
        verify(watchHistory, never()).updateById(org.mockito.ArgumentMatchers.any(WatchHistory.class));
    }

    @Test
    void nullAndInvalidIdentityDoesNotWrite() {
        service.recordWatch(null, 42L, 7L, 1, 2, false,
                null, null, 0, 0);
        service.recordWatch(11L, null, 7L, 1, 2, false,
                null, null, 0, 0);

        verify(watchHistory, never()).upsertProgress(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void sqlKeepsProgressMonotonicAndCountsRepeatOnlyAcrossSessions() throws Exception {
        Method method = WatchHistoryMapper.class.getMethod("upsertProgress", Long.class, Long.class,
                Long.class, double.class, double.class, int.class, String.class, String.class,
                double.class, double.class);
        Insert insert = method.getAnnotation(Insert.class);
        String sql = String.join(" ", insert.value());

        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("GREATEST(COALESCE(watch_duration"));
        assertTrue(sql.contains("GREATEST(COALESCE(last_position"));
        assertTrue(sql.contains("session_id <> VALUES(session_id)"));
        assertTrue(sql.contains("THEN 1 ELSE 0"));
    }
}

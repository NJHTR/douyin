package com.douyin.service;

import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserContentProfileMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryFeedbackHistoryContractTest {

    @Test
    void categoryFeedbackUsesBoundedMostRecentWatchSignals() throws Exception {
        Method query = WatchHistoryMapper.class.getMethod("findRecentCategoryFeedback",
                Long.class, LocalDateTime.class, int.class);
        Select select = query.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toUpperCase();

        assertTrue(sql.contains("ORDER BY UPDATE_TIME DESC, ID DESC"));
        assertTrue(sql.contains("LIMIT #{LIMIT}".toUpperCase()));

        WatchHistoryMapper history = mock(WatchHistoryMapper.class);
        when(history.findRecentCategoryFeedback(any(), any(), anyInt())).thenReturn(List.of());
        RecommendationEngine engine = new RecommendationEngine(
                mock(VideoMapper.class),
                mock(VideoContentMapper.class),
                mock(VideoExposureMapper.class),
                mock(UserContentProfileMapper.class),
                mock(LikeMapper.class),
                mock(FollowMapper.class),
                history,
                mock(VideoCollectMapper.class),
                mock(UserMapper.class),
                mock(UserProfileService.class));

        Method calculate = RecommendationEngine.class.getDeclaredMethod("calcCategoryFeedback", Long.class);
        calculate.setAccessible(true);
        calculate.invoke(engine, 42L);

        verify(history).findRecentCategoryFeedback(
                eq(42L), any(LocalDateTime.class), eq(RecommendationConfig.LIMIT_CATEGORY_FEEDBACK_HISTORY));
    }
}

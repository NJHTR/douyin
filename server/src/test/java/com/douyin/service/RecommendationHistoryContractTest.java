package com.douyin.service;

import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.WatchHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationHistoryContractTest {

    @Test
    void behaviorHistoryQueriesAreExplicitlyBounded() throws Exception {
        assertHasLimit(LikeMapper.class, "findRecentLikes");
        assertHasLimit(VideoCollectMapper.class, "findRecentCollects");
        assertHasLimit(VideoExposureMapper.class, "findRecentVideoIds");
        assertHasLimit(WatchHistoryMapper.class, "findRecentFinishedVideoIds");
    }

    private static void assertHasLimit(Class<?> mapper, String methodName) throws Exception {
        for (Method method : mapper.getMethods()) {
            if (method.getName().equals(methodName)) {
                Select select = method.getAnnotation(Select.class);
                assertTrue(select != null, mapper.getSimpleName() + "." + methodName + " must be annotated");
                assertTrue(String.join(" ", select.value()).contains("LIMIT #{limit}"),
                        mapper.getSimpleName() + "." + methodName + " must cap rows");
                return;
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}

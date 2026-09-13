package com.douyin.service;

import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationOrderingContractTest {

    @Test
    void boundedSignalQueriesHaveStableTieBreaks() throws Exception {
        assertSqlContains(LikeMapper.class, "findCoLikedVideoIds",
                new Class<?>[]{List.class, List.class, int.class},
                "ORDER BY co_count DESC, l2.video_id DESC");
        assertSqlContains(FollowMapper.class, "findMutualFollowIds",
                new Class<?>[]{Long.class, int.class},
                "ORDER BY a.create_time DESC, a.id DESC");
        assertSqlContains(VideoExposureMapper.class, "findRecentVideoIds",
                new Class<?>[]{Long.class, LocalDateTime.class, int.class},
                "ORDER BY exposure_time DESC, id DESC");
        assertSqlContains(WatchHistoryMapper.class, "findRecentFinishedVideoIds",
                new Class<?>[]{Long.class, LocalDateTime.class, int.class},
                "ORDER BY update_time DESC, id DESC");
        assertSqlContains(VideoMapper.class, "findRecentAuthorIds",
                new Class<?>[]{Long.class, int.class},
                "ORDER BY MAX(r.create_time) DESC, v.author_user_id DESC");
    }

    private static void assertSqlContains(Class<?> mapper, String methodName,
                                          Class<?>[] parameterTypes,
                                          String expected) throws Exception {
        Method method = mapper.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
        assertTrue(sql.contains(expected),
                () -> mapper.getSimpleName() + "." + methodName
                        + " must include stable ordering: " + expected);
    }
}

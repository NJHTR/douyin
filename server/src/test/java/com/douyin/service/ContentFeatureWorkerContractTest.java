package com.douyin.service;

import com.douyin.entity.Video;
import com.douyin.mapper.UserContentProfileMapper;
import com.douyin.mapper.VideoContentMapper;
import com.douyin.mapper.VideoExposureMapper;
import com.douyin.mapper.VideoMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ContentFeatureWorkerContractTest {

    @Test
    void duplicateVideoIdOnlyOccupiesOneQueueSlot() {
        ContentFeatureService service = newService();
        try {
            Video first = new Video();
            first.setId(42L);
            Video duplicate = new Video();
            duplicate.setId(42L);

            assertTrue(Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                    service, "enqueuePending", first)));
            assertEquals(Boolean.FALSE, ReflectionTestUtils.invokeMethod(
                    service, "enqueuePending", duplicate));

            Map<String, Object> status = service.getQueueStatus();
            assertEquals(1, status.get("queueSize"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void claimAndRecoverySqlEnforceLeaseAndBatchLimit() throws Exception {
        Method claimMethod = VideoContentMapper.class.getMethod(
                "tryClaim", Long.class, int.class);
        String claimSql = String.join(" ", claimMethod.getAnnotation(Update.class).value());
        assertTrue(claimSql.contains("extract_status IN (0, 2)"));
        assertTrue(claimSql.contains("extract_status = 3"));
        assertTrue(claimSql.contains("DATE_SUB(NOW(), INTERVAL #{leaseSeconds} SECOND)"));

        Method recoveryMethod = VideoContentMapper.class.getMethod(
                "findRecoverableVideoIds", int.class, int.class);
        String recoverySql = String.join(" ", recoveryMethod.getAnnotation(Select.class).value());
        assertTrue(recoverySql.contains("LIMIT #{limit}"));
        assertTrue(recoverySql.contains("update_time < DATE_SUB"));
    }

    private static ContentFeatureService newService() {
        return new ContentFeatureService(
                mock(VideoContentMapper.class),
                mock(VideoExposureMapper.class),
                mock(UserContentProfileMapper.class),
                mock(VideoMapper.class),
                mock(UserProfileService.class),
                mock(VideoTagService.class));
    }
}

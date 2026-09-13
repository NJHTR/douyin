package com.douyin.service;

import com.douyin.common.Result;
import com.douyin.config.MinioConfig;
import com.douyin.controller.ChunkUploadController;
import com.douyin.controller.UploadController;
import com.douyin.utils.JwtUtil;
import io.minio.MinioClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UploadRateLimitContractTest {
    private static final long USER_ID = 42L;

    private JwtUtil jwtUtil;
    private RedisCacheService redisCacheService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        redisCacheService = mock(RedisCacheService.class);
        request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(jwtUtil.getUserIdFromToken(anyString())).thenReturn(USER_ID);
    }

    @Test
    void presignIsRejectedBeforeMinioWhenUserLimitIsExceeded() {
        FileService fileService = mock(FileService.class);
        MinioClient minioClient = mock(MinioClient.class);
        UploadController controller = uploadController(fileService, minioClient);
        when(redisCacheService.rateLimit("upload:presign", "42", 20, Duration.ofMinutes(1)))
                .thenReturn(false);

        Result<Map<String, String>> result = controller.presign(
                "video.mp4", "video/mp4", 1024, request);

        assertEquals(429, result.getCode());
        verifyNoInteractions(fileService, minioClient);
    }

    @Test
    void multipartIsRejectedBeforeFileServiceWhenUserLimitIsExceeded() {
        FileService fileService = mock(FileService.class);
        UploadController controller = uploadController(fileService, mock(MinioClient.class));
        when(redisCacheService.rateLimit("upload:multipart", "42", 30, Duration.ofMinutes(1)))
                .thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "video.mp4", "video/mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'});

        Result<Map<String, String>> result = controller.uploadVideo(file, request);

        assertEquals(429, result.getCode());
        verifyNoInteractions(fileService);
    }

    @Test
    void chunkLimitIsScopedToUserAndUploadId() {
        MinioClient minioClient = mock(MinioClient.class);
        ChunkUploadController controller = new ChunkUploadController(
                minioClient, mock(MinioConfig.class), jwtUtil, redisCacheService);
        when(redisCacheService.rateLimit("upload:chunk", "42:upload-1234", 600, Duration.ofMinutes(1)))
                .thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "chunk", "application/octet-stream", new byte[]{1});

        Result<Map<String, Object>> result = controller.uploadChunk(
                file, "upload-1234", 0, 1, "video.mp4", request);

        assertEquals(429, result.getCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    void mergeLimitIsScopedToUser() {
        MinioClient minioClient = mock(MinioClient.class);
        ChunkUploadController controller = new ChunkUploadController(
                minioClient, mock(MinioConfig.class), jwtUtil, redisCacheService);
        when(redisCacheService.rateLimit("upload:merge", "42", 30, Duration.ofMinutes(1)))
                .thenReturn(false);

        Result<Map<String, Object>> result = controller.mergeChunks(Map.of(
                "uploadId", "upload-1234",
                "fileName", "video.mp4",
                "totalChunks", 1), request);

        assertEquals(429, result.getCode());
        verifyNoInteractions(minioClient);
    }

    private UploadController uploadController(FileService fileService, MinioClient minioClient) {
        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(controller, "minioClient", minioClient);
        ReflectionTestUtils.setField(controller, "minioConfig", mock(MinioConfig.class));
        ReflectionTestUtils.setField(controller, "redisCacheService", redisCacheService);
        return controller;
    }
}

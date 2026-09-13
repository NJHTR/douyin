package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.service.FileService;
import com.douyin.service.RedisCacheService;
import com.douyin.service.UploadPolicy;
import com.douyin.utils.JwtUtil;
import io.minio.*;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.io.InputStream;

/**
 * 文件上传到 MinIO, 配置 minio.enabled=true 后生效
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class UploadController {

    @Autowired
    private FileService fileService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private com.douyin.config.MinioConfig minioConfig;

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    @PostMapping("/video")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (!allow("upload:multipart", userId, 30)) return rateLimited();
        try {
            UploadPolicy.validateMultipart(file, UploadPolicy.Kind.VIDEO);
            String url = fileService.uploadVideo(file);
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("上传失败: {}", e.getMessage(), e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (!allow("upload:multipart", userId, 30)) return rateLimited();
        try {
            UploadPolicy.validateMultipart(file, UploadPolicy.Kind.IMAGE);
            String url = fileService.uploadImage(file);
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("上传失败: {}", e.getMessage(), e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/voice")
    public Result<Map<String, Object>> uploadVoice(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (!allow("upload:multipart", userId, 30)) return rateLimited();
        try {
            UploadPolicy.validateMultipart(file, UploadPolicy.Kind.VOICE);
            String url = fileService.uploadVideo(file);  // 音频也用 video 桶
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("上传失败: {}", e.getMessage(), e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    /** Issue a short-lived direct PUT URL. The object remains under quarantine until completed. */
    @PostMapping("/presign")
    public Result<Map<String, String>> presign(@RequestParam String fileName,
                                                @RequestParam String contentType,
                                                @RequestParam long size,
                                                HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        if (!allow("upload:presign", userId, 20)) return rateLimited();
        try {
            UploadPolicy.Kind kind;
            if (contentType != null && contentType.startsWith("image/")) kind = UploadPolicy.Kind.IMAGE;
            else if (contentType != null && contentType.startsWith("video/")) kind = UploadPolicy.Kind.VIDEO;
            else return Result.fail("仅支持图片或视频直传");
            UploadPolicy.validateName(fileName, kind);
            long max = kind == UploadPolicy.Kind.IMAGE ? UploadPolicy.MAX_IMAGE_BYTES : UploadPolicy.MAX_VIDEO_BYTES;
            if (size <= 0 || size > max) return Result.fail("文件超过大小限制");
            String kindPath = kind.name().toLowerCase();
            String bucket = kind == UploadPolicy.Kind.IMAGE
                    ? minioConfig.getBucketImage() : minioConfig.getBucketVideo();
            fileService.ensureBucket(bucket);
            String objectKey = "quarantine/" + userId + "/" + kindPath + "/"
                    + UUID.randomUUID() + "." + UploadPolicy.extension(fileName);
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(objectKey)
                    .expiry(10, TimeUnit.MINUTES).build());
            return Result.ok(Map.of("uploadUrl", url, "objectKey", objectKey, "expiresIn", "600"));
        } catch (Exception e) {
            return Result.fail("无法创建上传地址: " + e.getMessage());
        }
    }

    /** Verify the uploaded quarantine object and atomically promote it to the public bucket. */
    @PostMapping("/complete")
    public Result<Map<String, String>> complete(@RequestParam String objectKey,
                                                 @RequestParam String fileName,
                                                 HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        if (!allow("upload:complete", userId, 30)) return rateLimited();
        String prefix = "quarantine/" + userId + "/";
        if (!objectKey.startsWith(prefix)) return Result.fail("无权确认该文件");
        try {
            UploadPolicy.Kind kind;
            if (objectKey.startsWith(prefix + "image/")) kind = UploadPolicy.Kind.IMAGE;
            else if (objectKey.startsWith(prefix + "video/")) kind = UploadPolicy.Kind.VIDEO;
            else return Result.fail("隔离对象类型无效");
            UploadPolicy.validateName(fileName, kind);
            String bucket = kind == UploadPolicy.Kind.IMAGE
                    ? minioConfig.getBucketImage() : minioConfig.getBucketVideo();
            long max = kind == UploadPolicy.Kind.IMAGE ? UploadPolicy.MAX_IMAGE_BYTES : UploadPolicy.MAX_VIDEO_BYTES;
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
            if (stat.size() <= 0 || stat.size() > max) return Result.fail("对象大小无效");
            try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectKey).offset(0L).length(16L).build())) {
                UploadPolicy.validateMagic(fileName, kind, input);
            }
            String finalObject = UUID.randomUUID() + "." + UploadPolicy.extension(fileName);
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket).object(finalObject)
                    .source(CopySource.builder().bucket(bucket).object(objectKey).build())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return Result.ok(Map.of("url", bucket + "/" + finalObject));
        } catch (Exception e) {
            return Result.fail("上传确认失败: " + e.getMessage());
        }
    }

    private boolean allow(String action, Long userId, int maxRequests) {
        return redisCacheService.rateLimit(action, String.valueOf(userId), maxRequests, Duration.ofMinutes(1));
    }

    private <T> Result<T> rateLimited() {
        return Result.fail(429, "上传请求过于频繁，请稍后再试");
    }
}

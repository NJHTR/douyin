package com.douyin.service;

import com.douyin.config.MinioConfig;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Bounded cleanup for abandoned direct-upload quarantine objects. */
@Slf4j
@Component
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class UploadQuarantineCleanupJob {
    private static final int MAX_DELETES_PER_RUN = 1000;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public UploadQuarantineCleanupJob(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    @Scheduled(fixedDelayString = "${upload.quarantine-cleanup-ms:3600000}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        int remaining = MAX_DELETES_PER_RUN;
        for (String bucket : List.of(minioConfig.getBucketVideo(), minioConfig.getBucketImage())) {
            if (remaining <= 0) break;
            try {
                Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(bucket).prefix("quarantine/").recursive(true).build());
                for (Result<Item> result : objects) {
                    Item item = result.get();
                    if (item.lastModified().toInstant().isAfter(cutoff)) continue;
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket).object(item.objectName()).build());
                    if (--remaining <= 0) break;
                }
            } catch (Exception e) {
                log.warn("Upload quarantine cleanup failed for bucket={}: {}", bucket, e.getMessage());
            }
        }
    }
}

package com.douyin.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class UploadPolicyTest {
    @Test
    void rejectsTraversalAndUnsupportedExtensions() {
        assertThrows(IllegalArgumentException.class,
                () -> UploadPolicy.validateName("../secret.mp4", UploadPolicy.Kind.VIDEO));
        assertThrows(IllegalArgumentException.class,
                () -> UploadPolicy.validateName("payload.exe", UploadPolicy.Kind.VIDEO));
    }

    @Test
    void validatesMultipartSizeTypeAndName() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "cover.png", "image/png", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> UploadPolicy.validateMultipart(image, UploadPolicy.Kind.IMAGE));

        MockMultipartFile badType = new MockMultipartFile(
                "file", "cover.png", "video/mp4", new byte[]{1});
        assertThrows(IllegalArgumentException.class,
                () -> UploadPolicy.validateMultipart(badType, UploadPolicy.Kind.IMAGE));
    }

    @Test
    void recognizesCommonMediaMagicHeaders() throws Exception {
        assertDoesNotThrow(() -> UploadPolicy.validateMagic("x.jpg", UploadPolicy.Kind.IMAGE,
                new ByteArrayInputStream(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0}))); 
        assertDoesNotThrow(() -> UploadPolicy.validateMagic("x.mp4", UploadPolicy.Kind.VIDEO,
                new ByteArrayInputStream(new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'})));
        assertThrows(IllegalArgumentException.class, () -> UploadPolicy.validateMagic(
                "x.png", UploadPolicy.Kind.IMAGE, new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    @Test
    void chunkLimitsPreventUnboundedMultipartRequests() {
        MockMultipartFile chunk = new MockMultipartFile(
                "file", "chunk_0", "application/octet-stream", new byte[]{1});
        assertDoesNotThrow(() -> UploadPolicy.validateChunk(chunk, 0, 1, "movie.webm"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadPolicy.validateChunk(chunk, 0, UploadPolicy.MAX_CHUNKS + 1, "movie.webm"));
    }
}

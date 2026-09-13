package com.douyin.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/** Central upload limits and extension/content-type checks shared by all upload paths. */
public final class UploadPolicy {
    public static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_VOICE_BYTES = 20L * 1024 * 1024;
    public static final long MAX_CHUNK_BYTES = 8L * 1024 * 1024;
    public static final int MAX_CHUNKS = 1000;

    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov", "m4v");
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> VOICE_EXT = Set.of("mp3", "m4a", "wav", "ogg", "webm");

    private UploadPolicy() {}

    public static void validateMultipart(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        long max = switch (kind) {
            case VIDEO -> MAX_VIDEO_BYTES;
            case IMAGE -> MAX_IMAGE_BYTES;
            case VOICE -> MAX_VOICE_BYTES;
        };
        if (file.getSize() > max) throw new IllegalArgumentException("文件超过大小限制");
        validateName(file.getOriginalFilename(), kind);
        String contentType = normalize(file.getContentType());
        String prefix = switch (kind) {
            case VIDEO -> "video/";
            case IMAGE -> "image/";
            case VOICE -> "audio/";
        };
        if (!contentType.isEmpty() && !contentType.startsWith(prefix)
                && !(kind == Kind.VOICE && contentType.equals("video/webm"))) {
            throw new IllegalArgumentException("文件类型与上传接口不匹配");
        }
    }

    public static void validateChunk(MultipartFile file, int chunkIndex, int totalChunks, String fileName) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("分片不能为空");
        if (file.getSize() > MAX_CHUNK_BYTES) throw new IllegalArgumentException("分片超过 8MB 限制");
        if (totalChunks <= 0 || totalChunks > MAX_CHUNKS) throw new IllegalArgumentException("分片数超出范围");
        if (chunkIndex < 0 || chunkIndex >= totalChunks) throw new IllegalArgumentException("chunkIndex 越界");
        validateName(fileName, Kind.VIDEO);
    }

    public static void validateName(String fileName, Kind kind) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("文件名无效");
        }
        String ext = extension(fileName);
        Set<String> allowed = switch (kind) {
            case VIDEO -> VIDEO_EXT;
            case IMAGE -> IMAGE_EXT;
            case VOICE -> VOICE_EXT;
        };
        if (!allowed.contains(ext)) throw new IllegalArgumentException("不支持的文件扩展名");
    }

    /** Reject extension-only spoofing before an object leaves quarantine. */
    public static void validateMagic(String fileName, Kind kind, InputStream input) throws IOException {
        byte[] header = input.readNBytes(16);
        String ext = extension(fileName);
        boolean valid = switch (kind) {
            case IMAGE -> switch (ext) {
                case "jpg", "jpeg" -> startsWith(header, 0xff, 0xd8, 0xff);
                case "png" -> startsWith(header, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
                case "gif" -> ascii(header, "GIF87a") || ascii(header, "GIF89a");
                case "webp" -> ascii(header, "RIFF") && asciiAt(header, 8, "WEBP");
                default -> false;
            };
            case VIDEO -> switch (ext) {
                case "mp4", "mov", "m4v" -> asciiAt(header, 4, "ftyp");
                case "webm" -> startsWith(header, 0x1a, 0x45, 0xdf, 0xa3);
                default -> false;
            };
            case VOICE -> header.length >= 4;
        };
        if (!valid) throw new IllegalArgumentException("文件内容与扩展名不匹配");
    }

    public static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String normalize(String contentType) {
        if (contentType == null) return "";
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] actual, int... expected) {
        if (actual.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((actual[i] & 0xff) != expected[i]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] actual, String expected) {
        return asciiAt(actual, 0, expected);
    }

    private static boolean asciiAt(byte[] actual, int offset, String expected) {
        if (actual.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) {
            if (actual[offset + i] != (byte) expected.charAt(i)) return false;
        }
        return true;
    }

    public enum Kind { VIDEO, IMAGE, VOICE }
}

package com.douyin.service;

import java.util.Locale;

/**
 * Public feed contract shared by HTTP clients and the recommendation layer.
 * Unknown values intentionally fall back to HOME for backwards compatibility.
 */
public enum FeedChannel {
    HOME,
    HOT,
    FOLLOWING,
    FRIENDS,
    LIVE,
    LONG_VIDEO,
    EXPERIENCE;

    public static FeedChannel parse(String raw) {
        if (raw == null || raw.isBlank()) return HOME;
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return HOME;
        }
    }

    public boolean isPersonalized() {
        return this == HOME || this == EXPERIENCE || this == LONG_VIDEO;
    }

    public boolean isGlobal() {
        return this == HOT;
    }
}

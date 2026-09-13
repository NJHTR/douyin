package com.douyin.service;

/** Raised when Redis is required for a correctness-critical coordination operation. */
public class RedisCoordinationUnavailableException extends RuntimeException {
    public RedisCoordinationUnavailableException(String operation, Throwable cause) {
        super("Redis coordination unavailable during " + operation, cause);
    }
}

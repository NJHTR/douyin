package com.douyin.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilSecurityTest {

    @Test
    void emptySecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("", 60_000));
    }

    @Test
    void weakSecretIsRejected() {
        String weak = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new JwtUtil(weak, 60_000));
    }
}

package com.douyin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisCacheService redisCacheService;

    @Value("${spring.mail.username}")
    private String from;

    /** 内存存储验证码, key=email, value=code */
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final Map<String, Object> emailLocks = new ConcurrentHashMap<>();
    private static final Duration SEND_WINDOW = Duration.ofSeconds(60);

    public EmailService(JavaMailSender mailSender, RedisCacheService redisCacheService) {
        this.mailSender = mailSender;
        this.redisCacheService = redisCacheService;
    }

    /**
     * Sends at most one code per email per minute. The idempotency key makes
     * client retries safe while the Redis rate-limit protects across nodes.
     */
    public SendCodeResult sendCode(String to, String idempotencyKey) {
        String email = normalizeEmail(to);
        String emailKey = hash(email);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestKey = idempotencyKey.trim();
            if (requestKey.length() > 128) requestKey = requestKey.substring(0, 128);
            if (!redisCacheService.tryAcquireIdempotent("email-code:" + emailKey + ":" + hash(requestKey))) {
                return new SendCodeResult(true, true, 60);
            }
        }
        if (!redisCacheService.rateLimit("email-code", emailKey, 1, SEND_WINDOW)) {
            return new SendCodeResult(false, false, 60);
        }
        synchronized (emailLocks.computeIfAbsent(emailKey, ignored -> new Object())) {
            CodeEntry current = codeStore.get(email);
            if (current != null) {
                long elapsed = System.currentTimeMillis() - current.ts;
                if (elapsed < SEND_WINDOW.toMillis()) {
                    return new SendCodeResult(false, false, Math.max(1, (int) Math.ceil((SEND_WINDOW.toMillis() - elapsed) / 1000d)));
                }
            }
        String code = String.format("%06d", new Random().nextInt(1000000));
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(email);
        msg.setSubject("NIVO 登录验证码");
        msg.setText("您的 NIVO 登录验证码是：" + code + "，有效期5分钟。请勿转发给他人。");
        mailSender.send(msg);
        codeStore.put(email, new CodeEntry(code, System.currentTimeMillis()));
        }
        return new SendCodeResult(true, false, 60);
    }

    public SendCodeResult sendCode(String to) { return sendCode(to, null); }

    public boolean allowRequestFromIp(String ip) {
        String value = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        return redisCacheService.rateLimit("email-code-ip", hash(value), 5, Duration.ofMinutes(10));
    }

    /** 验证并返回是否通过, 通过后删除验证码 */
    public boolean verify(String email, String code) {
        CodeEntry entry = codeStore.get(normalizeEmail(email));
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.ts > 5 * 60 * 1000) {
            codeStore.remove(normalizeEmail(email));
            return false;
        }
        if (entry.code.equals(code)) {
            codeStore.remove(email);
            return true;
        }
        return false;
    }

    private record CodeEntry(String code, long ts) {}

    public record SendCodeResult(boolean accepted, boolean duplicate, int retryAfterSeconds) {}

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

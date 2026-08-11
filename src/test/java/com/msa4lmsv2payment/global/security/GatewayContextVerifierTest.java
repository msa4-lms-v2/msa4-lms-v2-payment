package com.msa4lmsv2payment.global.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayContextVerifierTest {

    private static final String SECRET = "gateway-context-test-secret-32-bytes-minimum";
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final String TIMESTAMP = String.valueOf(NOW.getEpochSecond());

    private final GatewayContextVerifier verifier = new GatewayContextVerifier(
            new GatewayContextProperties(SECRET, Duration.ofMinutes(2)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 서명이_정상이고_허용시간_안이면_유효하다() {
        String signature = sign("1", "STUDENT", TIMESTAMP, "GET", "/api/payments/student-tuition");

        boolean valid = verifier.isValid(
                "1",
                "STUDENT",
                TIMESTAMP,
                "GET",
                "/api/payments/student-tuition",
                signature);

        assertThat(valid).isTrue();
    }

    @Test
    void 요청경로가_바뀌면_서명이_거부된다() {
        String signature = sign("1", "STUDENT", TIMESTAMP, "GET", "/api/payments/student-tuition");

        boolean valid = verifier.isValid(
                "1",
                "STUDENT",
                TIMESTAMP,
                "GET",
                "/api/payments/admin-tuition-bills",
                signature);

        assertThat(valid).isFalse();
    }

    @Test
    void 허용시간을_지난_서명은_거부된다() {
        String expiredTimestamp = String.valueOf(NOW.minus(Duration.ofMinutes(3)).getEpochSecond());
        String signature = sign("1", "STUDENT", expiredTimestamp, "GET", "/api/payments/student-tuition");

        boolean valid = verifier.isValid(
                "1",
                "STUDENT",
                expiredTimestamp,
                "GET",
                "/api/payments/student-tuition",
                signature);

        assertThat(valid).isFalse();
    }

    @Test
    void 허용되지_않은_역할은_거부된다() {
        String signature = sign("1", "ROOT", TIMESTAMP, "GET", "/api/payments/student-tuition");

        boolean valid = verifier.isValid(
                "1",
                "ROOT",
                TIMESTAMP,
                "GET",
                "/api/payments/student-tuition",
                signature);

        assertThat(valid).isFalse();
    }

    private String sign(String userId, String role, String timestamp, String method, String requestUri) {
        try {
            String canonicalValue = String.join("\n", userId, role, timestamp, method, requestUri);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonicalValue.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.msa4lmsv2payment.global.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Component
public class GatewayContextVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Set<String> ALLOWED_ROLES = Set.of("STUDENT", "PROFESSOR", "ADMIN", "SYSTEM");

    private final byte[] secret;
    private final Duration allowedClockSkew;
    private final Clock clock;

    public GatewayContextVerifier(GatewayContextProperties properties, Clock clock) {
        this.secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        this.allowedClockSkew = properties.allowedClockSkew() == null
                ? Duration.ofMinutes(2)
                : properties.allowedClockSkew();
        this.clock = clock;
    }

    public boolean isValid(String userId,
                           String role,
                           String timestamp,
                           String method,
                           String requestUri,
                           String providedSignature) {
        if (secret.length < MINIMUM_SECRET_BYTES
                || isBlank(userId)
                || isBlank(role)
                || isBlank(timestamp)
                || isBlank(method)
                || isBlank(requestUri)
                || isBlank(providedSignature)) {
            return false;
        }

        if (!isValidUserId(userId) || !ALLOWED_ROLES.contains(role)) {
            return false;
        }

        if (!isTimestampAllowed(timestamp)) {
            return false;
        }

        byte[] expectedSignature = sign(canonicalValue(userId, role, timestamp, method, requestUri));
        try {
            byte[] actualSignature = Base64.getUrlDecoder().decode(providedSignature);
            return MessageDigest.isEqual(expectedSignature, actualSignature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTimestampAllowed(String timestamp) {
        try {
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Instant now = clock.instant();
            return !signedAt.isBefore(now.minus(allowedClockSkew))
                    && !signedAt.isAfter(now.plus(allowedClockSkew));
        } catch (NumberFormatException | DateTimeException e) {
            return false;
        }
    }

    private boolean isValidUserId(String userId) {
        try {
            return Long.parseLong(userId) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Gateway 사용자 컨텍스트 서명을 검증할 수 없습니다.", e);
        }
    }

    private String canonicalValue(String userId,
                                  String role,
                                  String timestamp,
                                  String method,
                                  String requestUri) {
        return String.join("\n", userId, role, timestamp, method.toUpperCase(Locale.ROOT), requestUri);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

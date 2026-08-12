package com.msa4lmsv2payment.global.security;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class GatewayContextVerifier {

    private static final Set<String> ALLOWED_ROLES = Set.of("STUDENT", "PROFESSOR", "ADMIN");

    public boolean isValid(String userId, String role) {
        return isValidUserId(userId) && role != null && ALLOWED_ROLES.contains(role);
    }

    private boolean isValidUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(userId) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

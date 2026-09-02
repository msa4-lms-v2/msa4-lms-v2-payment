package com.msa4lmsv2payment.global.security;

public record CurrentUser(Long id, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

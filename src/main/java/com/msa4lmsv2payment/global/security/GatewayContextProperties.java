package com.msa4lmsv2payment.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.gateway-context")
public record GatewayContextProperties(
        String secret,
        Duration allowedClockSkew
) {
}

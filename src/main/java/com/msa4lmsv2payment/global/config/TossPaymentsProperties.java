package com.msa4lmsv2payment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentsProperties(
        String baseUrl,
        String secretKey,
        String clientKey,
        long connectTimeout,
        long readTimeout
) {
}

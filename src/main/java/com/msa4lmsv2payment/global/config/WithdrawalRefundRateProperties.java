package com.msa4lmsv2payment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 자퇴 환불률 - 고등교육법 시행령 등록금 반환 기준. 학칙이 다르면 값만 바꾸면 되도록 설정으로 뺐다.
 */
@ConfigurationProperties(prefix = "payment.refund.withdrawal")
public record WithdrawalRefundRateProperties(
        BigDecimal rateBeforeStart,
        BigDecimal rateUnderOneThird,
        BigDecimal rateUnderHalf,
        BigDecimal rateUnderTwoThirds,
        BigDecimal rateAfterTwoThirds
) {
}

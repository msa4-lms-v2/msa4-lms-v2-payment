package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.global.config.WithdrawalRefundRateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 자퇴 환불률 계산 - 고등교육법 시행령 반환 기준을 개강일~종강일 경과 비율로 근사해 적용한다.
 * "수업일수 대비 경과 비율" 기준이라 정확한 수업일수(공휴일 제외) 대신 달력일수로 근사한다 - 15주 학기 기준
 * 1/3·1/2·2/3 구간 경계가 이 근사로 크게 흔들리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawalRefundRateCalculatorService {

    private final WithdrawalRefundRateProperties properties;

    public BigDecimal calculate(LocalDate effectiveDate, LocalDate semesterStart, LocalDate semesterEnd) {
        if (effectiveDate.isBefore(semesterStart)) {
            return properties.rateBeforeStart();
        }

        long totalDays = ChronoUnit.DAYS.between(semesterStart, semesterEnd);
        long elapsedDays = ChronoUnit.DAYS.between(semesterStart, effectiveDate);
        double ratio = totalDays == 0 ? 1.0 : elapsedDays / (double) totalDays;

        if (ratio < 1.0 / 3) {
            return properties.rateUnderOneThird();
        }
        if (ratio < 1.0 / 2) {
            return properties.rateUnderHalf();
        }
        if (ratio < 2.0 / 3) {
            return properties.rateUnderTwoThirds();
        }
        return properties.rateAfterTwoThirds();
    }
}

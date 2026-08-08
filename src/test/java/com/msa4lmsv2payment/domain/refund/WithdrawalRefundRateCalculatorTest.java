package com.msa4lmsv2payment.domain.refund;

import com.msa4lmsv2payment.global.config.WithdrawalRefundRateProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawalRefundRateCalculatorTest {

    private static final LocalDate SEMESTER_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEMESTER_END = LocalDate.of(2026, 12, 18);

    private final WithdrawalRefundRateProperties properties = new WithdrawalRefundRateProperties(
            BigDecimal.ONE,
            new BigDecimal("0.8333"),
            new BigDecimal("0.6667"),
            new BigDecimal("0.5"),
            BigDecimal.ZERO
    );

    private final WithdrawalRefundRateCalculator calculator = new WithdrawalRefundRateCalculator(properties);

    // SCRUM-166/63의 근거 - MY-PLAN_payment.md 7-2절 고등교육법 시행령 반환 기준
    @Test
    void 개강_전_자퇴는_100퍼센트_반환() {
        BigDecimal rate = calculator.calculate(
                LocalDateTime.of(2026, 8, 31, 10, 0), SEMESTER_START, SEMESTER_END);

        assertThat(rate).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void 수업일수_1_3_경과_전은_5_6_반환() {
        BigDecimal rate = calculator.calculate(
                LocalDateTime.of(2026, 9, 1, 10, 0), SEMESTER_START, SEMESTER_END);

        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.8333"));
    }

    @Test
    void 발표일_9월18일은_5_6_구간이다() {
        BigDecimal rate = calculator.calculate(
                LocalDateTime.of(2026, 9, 18, 10, 0), SEMESTER_START, SEMESTER_END);

        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.8333"));
    }

    @Test
    void 수업일수_2_3_경과_후는_0퍼센트_반환() {
        BigDecimal rate = calculator.calculate(
                LocalDateTime.of(2026, 12, 1, 10, 0), SEMESTER_START, SEMESTER_END);

        assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

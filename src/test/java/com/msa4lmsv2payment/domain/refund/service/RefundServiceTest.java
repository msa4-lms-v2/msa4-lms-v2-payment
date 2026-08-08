package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.WithdrawalRefundRateCalculator;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.AcademicWithdrawalHistoryResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private AcademicClient academicClient;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    @InjectMocks
    private RefundService refundService;

    private final WithdrawalRefundRateCalculator calculator = new WithdrawalRefundRateCalculator(
            new com.msa4lmsv2payment.global.config.WithdrawalRefundRateProperties(
                    BigDecimal.ONE, new BigDecimal("0.8333"), new BigDecimal("0.6667"), new BigDecimal("0.5"), BigDecimal.ZERO));

    private static TuitionBill tuitionBill(Long id, BigDecimal billingAmount) {
        TuitionBill bill = new TuitionBill();
        setField(bill, "id", id);
        setField(bill, "studentId", 20260001L);
        setField(bill, "semesterId", 5L);
        setField(bill, "billingAmount", billingAmount);
        setField(bill, "dueDate", LocalDate.of(2026, 9, 1));
        setField(bill, "status", TuitionBillStatus.UNPAID);
        return bill;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = TuitionBill.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // SCRUM-63: 자퇴 예상 환불금 조회
    @Test
    void 예상_환불금은_고지금액에_환불률을_곱한_값이고_저장하지_않는다() {
        setCalculatorField();
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(academicClient.findLatestWithdrawalHistory(20260001L))
                .thenReturn(new AcademicWithdrawalHistoryResponse("ENROLLED", "ON_LEAVE", LocalDateTime.of(2026, 9, 18, 10, 0)));
        when(academicClient.findSemester(5L))
                .thenReturn(new AcademicSemesterResponse(5L, "SECOND", true, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 18)));

        var result = refundService.estimateWithdrawalRefund(student, 1L);

        assertThat(result.refundRate()).isEqualByComparingTo(new BigDecimal("0.8333"));
        assertThat(result.estimatedRefundAmount()).isEqualByComparingTo(BigDecimal.valueOf(4_200_000).multiply(new BigDecimal("0.8333")));
    }

    // SCRUM-166: 자퇴 처리일 기준 환불률 적용
    @Test
    void 최초_적용은_새_환불_행을_생성한다() {
        setCalculatorField();
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        when(academicClient.findLatestWithdrawalHistory(20260001L))
                .thenReturn(new AcademicWithdrawalHistoryResponse("ENROLLED", "ON_LEAVE", LocalDateTime.of(2026, 9, 18, 10, 0)));
        when(academicClient.findSemester(5L))
                .thenReturn(new AcademicSemesterResponse(5L, "SECOND", true, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 18)));
        when(refundRepository.findByTuitionBillIdAndRefundType(1L, RefundType.WITHDRAWAL)).thenReturn(Optional.empty());
        when(refundRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        RefundResponseDTO result = refundService.applyWithdrawalRefundRate(admin, new WithdrawalRefundRateRequestDTO(1L));

        assertThat(result.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(result.refundType()).isEqualTo(RefundType.WITHDRAWAL);
    }

    // 비기능 #19: 동일 환불 요청은 중복 실행되지 않는다
    @Test
    void 이미_있는_환불_요청은_새로_만들지_않고_비율만_갱신한다() {
        setCalculatorField();
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        when(academicClient.findLatestWithdrawalHistory(20260001L))
                .thenReturn(new AcademicWithdrawalHistoryResponse("ENROLLED", "ON_LEAVE", LocalDateTime.of(2026, 9, 18, 10, 0)));
        when(academicClient.findSemester(5L))
                .thenReturn(new AcademicSemesterResponse(5L, "SECOND", true, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 18)));
        Refund existing = new Refund(1L, RefundType.WITHDRAWAL, BigDecimal.ZERO, BigDecimal.ZERO, RefundStatus.REQUESTED);
        when(refundRepository.findByTuitionBillIdAndRefundType(1L, RefundType.WITHDRAWAL)).thenReturn(Optional.of(existing));

        refundService.applyWithdrawalRefundRate(admin, new WithdrawalRefundRateRequestDTO(1L));

        org.mockito.Mockito.verify(refundRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getRefundRate()).isEqualByComparingTo(new BigDecimal("0.8333"));
    }

    private void setCalculatorField() {
        try {
            Field field = RefundService.class.getDeclaredField("withdrawalRefundRateCalculator");
            field.setAccessible(true);
            field.set(refundService, calculator);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

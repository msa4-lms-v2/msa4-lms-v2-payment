package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.AcademicWithdrawalResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.RefundManualReviewRequiredException;
import com.msa4lmsv2payment.global.error.RefundVerificationPersistFailedException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 자퇴 환불률 산정 장애 격리(PENDING_ACADEMIC_VERIFICATION/MANUAL_REVIEW_REQUIRED) 단위 테스트.
 * Academic 호출·저장을 전부 Mock으로 대체해 DB/스프링 컨텍스트 없이 상태 전이만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceAcademicVerificationTest {

    private static final CurrentUser STUDENT = new CurrentUser(1L, "STUDENT");

    @Mock RefundRepository refundRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock TuitionBillService tuitionBillService;
    @Mock PaymentService paymentService;
    @Mock VirtualAccountService virtualAccountService;
    @Mock AcademicClient academicClient;
    @Mock TossPaymentsClient tossPaymentsClient;
    @Mock WithdrawalRefundRateCalculatorService withdrawalRefundRateCalculator;
    @Mock RefundRecorderService refundRecorder;
    @Mock TuitionBill tuitionBill;

    private RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(refundRepository, paymentRepository, tuitionBillService, paymentService,
                virtualAccountService, academicClient, tossPaymentsClient, withdrawalRefundRateCalculator, refundRecorder);
    }

    @Test
    void Academic_스냅샷_미반영이면_PENDING_ACADEMIC_VERIFICATION으로_저장하고_202에_해당하는_응답을_돌려준다() {
        when(tuitionBillService.getOwnedTuitionBillOrThrow(STUDENT, 10L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(10L);
        when(academicClient.findWithdrawal(5L)).thenThrow(new AcademicResourceNotFoundException("스냅샷 미반영"));
        when(refundRepository.findByTuitionBillIdAndRefundType(10L, RefundType.WITHDRAWAL)).thenReturn(Optional.empty());
        when(refundRecorder.savePendingAcademicVerification(eq(1L), any(Refund.class), eq(10L)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        RefundResponseDTO response = service.applyWithdrawalRefundRate(STUDENT, new WithdrawalRefundRateRequestDTO(10L, 5L));

        assertThat(response.status()).isEqualTo(RefundStatus.PENDING_ACADEMIC_VERIFICATION);
        assertThat(response.withdrawalId()).isEqualTo(5L);
        verify(refundRecorder).savePendingAcademicVerification(eq(1L), any(Refund.class), eq(10L));
        verify(academicClient, never()).findSemester(any());
    }

    @Test
    void PENDING_저장_자체가_실패하면_503_예외를_던진다() {
        when(tuitionBillService.getOwnedTuitionBillOrThrow(STUDENT, 10L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(10L);
        when(academicClient.findWithdrawal(5L)).thenThrow(new AcademicResourceNotFoundException("스냅샷 미반영"));
        when(refundRepository.findByTuitionBillIdAndRefundType(10L, RefundType.WITHDRAWAL)).thenReturn(Optional.empty());
        when(refundRecorder.savePendingAcademicVerification(eq(1L), any(Refund.class), eq(10L)))
                .thenThrow(new DataAccessResourceFailureException("DB 접속 불가"));

        assertThatThrownBy(() -> service.applyWithdrawalRefundRate(STUDENT, new WithdrawalRefundRateRequestDTO(10L, 5L)))
                .isInstanceOf(RefundVerificationPersistFailedException.class);
    }

    @Test
    void 이미_MANUAL_REVIEW_REQUIRED인_건은_재시도해도_예외를_던진다() {
        Refund existing = new Refund(10L, RefundType.WITHDRAWAL, BigDecimal.ZERO, BigDecimal.ZERO, RefundStatus.REQUESTED);
        existing.markPendingAcademicVerification(5L);
        existing.requireManualReview();

        when(tuitionBillService.getOwnedTuitionBillOrThrow(STUDENT, 10L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(10L);
        when(academicClient.findWithdrawal(5L)).thenThrow(new AcademicResourceNotFoundException("스냅샷 미반영"));
        when(refundRepository.findByTuitionBillIdAndRefundType(10L, RefundType.WITHDRAWAL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.applyWithdrawalRefundRate(STUDENT, new WithdrawalRefundRateRequestDTO(10L, 5L)))
                .isInstanceOf(RefundManualReviewRequiredException.class);
        verify(refundRecorder, never()).savePendingAcademicVerification(any(), any(), any());
    }

    @Test
    void 재검증_스케줄러_호출이_성공하면_REQUESTED로_확정하고_true를_반환한다() {
        Refund pending = new Refund(10L, RefundType.WITHDRAWAL, BigDecimal.ZERO, BigDecimal.ZERO, RefundStatus.REQUESTED);
        pending.markPendingAcademicVerification(5L);

        when(refundRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(tuitionBillService.getTuitionBillOrThrow(10L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(10L);
        when(tuitionBill.getStudentId()).thenReturn(100L);
        when(tuitionBill.getSemesterId()).thenReturn(7L);
        when(academicClient.findWithdrawal(5L)).thenReturn(new AcademicWithdrawalResponse(5L, 100L, LocalDate.of(2026, 3, 1)));
        when(academicClient.findSemester(7L)).thenReturn(new AcademicSemesterResponse(7L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)));
        when(withdrawalRefundRateCalculator.calculate(any(), any(), any())).thenReturn(BigDecimal.valueOf(0.8));
        when(paymentRepository.sumSucceededAmount(10L)).thenReturn(BigDecimal.valueOf(1_000_000));
        when(refundRepository.sumSucceededAmount(10L)).thenReturn(BigDecimal.ZERO);

        boolean resolved = service.retryPendingAcademicVerification(99L);

        assertThat(resolved).isTrue();
        assertThat(pending.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRecorder).saveRateApplied(eq(0L), eq(pending), eq(10L), any(BigDecimal.class), eq(BigDecimal.valueOf(0.8)));
    }

    @Test
    void 재검증_스케줄러_호출이_여전히_실패하면_저장_없이_false를_반환한다() {
        Refund pending = new Refund(10L, RefundType.WITHDRAWAL, BigDecimal.ZERO, BigDecimal.ZERO, RefundStatus.REQUESTED);
        pending.markPendingAcademicVerification(5L);

        when(refundRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(tuitionBillService.getTuitionBillOrThrow(10L)).thenReturn(tuitionBill);
        when(academicClient.findWithdrawal(5L)).thenThrow(new AcademicResourceNotFoundException("여전히 미반영"));

        boolean resolved = service.retryPendingAcademicVerification(99L);

        assertThat(resolved).isFalse();
        assertThat(pending.getStatus()).isEqualTo(RefundStatus.PENDING_ACADEMIC_VERIFICATION);
        verify(refundRecorder, never()).saveRateApplied(any(), any(), any(), any(), any());
    }

    @Test
    void 유예_시간을_넘긴_건은_MANUAL_REVIEW_REQUIRED로_전환한다() {
        Refund pending = new Refund(10L, RefundType.WITHDRAWAL, BigDecimal.ZERO, BigDecimal.ZERO, RefundStatus.REQUESTED);
        pending.markPendingAcademicVerification(5L);

        when(refundRepository.findById(99L)).thenReturn(Optional.of(pending));
        when(refundRecorder.saveManualReviewRequired(eq(0L), eq(pending), any())).thenReturn(pending);

        service.escalateToManualReview(99L);

        assertThat(pending.getStatus()).isEqualTo(RefundStatus.MANUAL_REVIEW_REQUIRED);
        verify(refundRecorder).saveManualReviewRequired(eq(0L), eq(pending), any());
    }

    @Test
    void 이미_다른_상태인_건은_전환하지_않는다() {
        Refund requested = new Refund(10L, RefundType.WITHDRAWAL, BigDecimal.valueOf(500_000), BigDecimal.valueOf(0.5), RefundStatus.REQUESTED);
        when(refundRepository.findById(anyLong())).thenReturn(Optional.of(requested));

        service.escalateToManualReview(99L);

        assertThat(requested.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRecorder, never()).saveManualReviewRequired(any(), any(), any());
    }
}

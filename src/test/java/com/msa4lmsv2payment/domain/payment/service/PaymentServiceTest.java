package com.msa4lmsv2payment.domain.payment.service;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.global.error.PaymentAmountMismatchException;
import com.msa4lmsv2payment.global.error.PaymentNotFoundException;
import com.msa4lmsv2payment.global.error.PaymentResultMismatchException;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.payment.request.CheckoutSessionRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentAmountValidationRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentResultSyncRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentStatusRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PgPaymentRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private ScholarshipService scholarshipService;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private PaymentResultRecorder paymentResultRecorder;
    @Mock
    private TuitionOverpaymentGuard tuitionOverpaymentGuard;

    @InjectMocks
    private PaymentService paymentService;

    private static TuitionBill tuitionBill(Long id, BigDecimal amount) {
        TuitionBill bill = new TuitionBill(20260001L, 5L, amount, LocalDate.of(2026, 9, 1), TuitionBillStatus.UNPAID, 1L);
        setField(bill, "id", id);
        return bill;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // SCRUM-51: 결제 금액 검증
    @Test
    void 서버_계산_금액과_같으면_유효하다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(scholarshipService.calculateAllocation(eq(student), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(4_200_000)));

        var result = paymentService.validateAmount(student, new PaymentAmountValidationRequestDTO(1L, BigDecimal.valueOf(4_200_000)));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void 서버_계산_금액과_다르면_무효다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(scholarshipService.calculateAllocation(eq(student), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(4_200_000)));

        var result = paymentService.validateAmount(student, new PaymentAmountValidationRequestDTO(1L, BigDecimal.valueOf(1_000)));

        assertThat(result.valid()).isFalse();
    }

    // SCRUM-111: 결제창 연동
    @Test
    void 체크아웃_세션은_orderId를_PAY_접두어로_생성한다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipService.calculateAllocation(eq(student), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(4_200_000)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setField(p, "id", 7L);
            return p;
        });

        var result = paymentService.createCheckoutSession(student, new CheckoutSessionRequestDTO(1L, PaymentMethod.CARD));

        assertThat(result.orderId()).isEqualTo("PAY-7");
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(4_200_000));
    }

    // SCRUM-115: PG 결제 요청
    @Test
    void 토스_confirm_결과가_DONE이면_결제가_성공처리된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L, BigDecimal.valueOf(4_200_000)));
        when(tossPaymentsClient.confirmPayment("pk_test", "PAY-7", BigDecimal.valueOf(4_200_000), "idem-1"))
                .thenReturn(new TossPaymentResponse("pk_test", "PAY-7", "DONE", 4_200_000L));
        when(paymentResultRecorder.saveWithAudit(eq(1L), any(), eq("DONE"))).thenAnswer(inv -> inv.getArgument(1));

        var result = paymentService.requestPgPayment(student,
                new PgPaymentRequestDTO("PAY-7", "pk_test", BigDecimal.valueOf(4_200_000)), "idem-1");

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.pgTransactionId()).isEqualTo("pk_test");
    }

    @Test
    void 토스_confirm_결과가_DONE이_아니면_결제가_실패처리된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L, BigDecimal.valueOf(4_200_000)));
        when(tossPaymentsClient.confirmPayment("pk_test", "PAY-7", BigDecimal.valueOf(4_200_000), "idem-1"))
                .thenReturn(new TossPaymentResponse("pk_test", "PAY-7", "ABORTED", 4_200_000L));
        when(paymentResultRecorder.saveWithAudit(eq(1L), any(), eq("ABORTED"))).thenAnswer(inv -> inv.getArgument(1));

        var result = paymentService.requestPgPayment(student,
                new PgPaymentRequestDTO("PAY-7", "pk_test", BigDecimal.valueOf(4_200_000)), "idem-1");

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 프론트가_보낸_금액이_다르면_토스를_부르지_않고_거부한다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(tuitionBill(1L, BigDecimal.valueOf(4_200_000)));

        assertThatThrownBy(() -> paymentService.requestPgPayment(student,
                new PgPaymentRequestDTO("PAY-7", "pk_test", BigDecimal.valueOf(1_000)), "idem-1"))
                .isInstanceOf(PaymentAmountMismatchException.class);
    }

    @Test
    void 존재하지_않는_orderId는_PaymentNotFoundException() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.requestPgPayment(student,
                new PgPaymentRequestDTO("PAY-999", "pk_test", BigDecimal.valueOf(1_000)), "idem-1"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // SCRUM-110: 결제 성공·실패 처리(ADMIN 복구)
    @Test
    void 관리자는_토스_실제_상태를_재조회해서_동기화한다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.getPayment("pk_test"))
                .thenReturn(new TossPaymentResponse("pk_test", "PAY-7", "DONE", 4_200_000L));
        when(paymentResultRecorder.saveWithAudit(eq(1L), any(), eq("DONE"))).thenAnswer(inv -> inv.getArgument(1));

        var result = paymentService.syncPaymentResult(admin, new PaymentResultSyncRequestDTO("PAY-7", "pk_test"));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void PG_응답의_orderId가_다르면_로컬_결제에_반영하지_않는다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.getPayment("pk_test"))
                .thenReturn(new TossPaymentResponse("pk_test", "PAY-999", "DONE", 4_200_000L));

        assertThatThrownBy(() -> paymentService.syncPaymentResult(
                admin, new PaymentResultSyncRequestDTO("PAY-7", "pk_test")))
                .isInstanceOf(PaymentResultMismatchException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        verifyNoInteractions(paymentResultRecorder);
    }

    @Test
    void 성공한_결제는_실패_응답으로_역전되지_않는다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        Payment payment = new Payment(1L, 20260001L, BigDecimal.valueOf(4_200_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        setField(payment, "id", 7L);
        payment.succeed("pk_test");
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.getPayment("pk_test"))
                .thenReturn(new TossPaymentResponse("pk_test", "PAY-7", "ABORTED", 4_200_000L));

        assertThatThrownBy(() -> paymentService.syncPaymentResult(
                admin, new PaymentResultSyncRequestDTO("PAY-7", "pk_test")))
                .isInstanceOf(PaymentResultMismatchException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verifyNoInteractions(paymentResultRecorder);
    }

    // SCRUM-112: 납부 상태 반영
    @Test
    void 완납이면_PAID로_바뀐다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        when(scholarshipService.calculateAllocation(eq(admin), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(4_200_000)));
        when(paymentRepository.sumSucceededAmount(1L)).thenReturn(BigDecimal.valueOf(4_200_000));

        paymentService.recalculateTuitionStatus(admin, new PaymentStatusRequestDTO(1L));

        org.mockito.Mockito.verify(tuitionBillService).changeStatus(1L, TuitionBillStatus.PAID);
    }

    @Test
    void 일부만_냈으면_PARTIAL로_바뀐다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        when(scholarshipService.calculateAllocation(eq(admin), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(4_200_000)));
        when(paymentRepository.sumSucceededAmount(1L)).thenReturn(BigDecimal.valueOf(1_000_000));

        paymentService.recalculateTuitionStatus(admin, new PaymentStatusRequestDTO(1L));

        org.mockito.Mockito.verify(tuitionBillService).changeStatus(1L, TuitionBillStatus.PARTIAL);
    }

    // SCRUM-113: 납부 현황 반영
    @Test
    void 납부현황은_장학금_차감후_잔액을_계산한다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipService.calculateAllocation(eq(student), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(2_200_000)));
        when(paymentRepository.sumSucceededAmount(1L)).thenReturn(BigDecimal.valueOf(1_200_000));

        var result = paymentService.getPaymentSummary(student, 1L);

        assertThat(result.totalScholarshipAmount()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        assertThat(result.totalPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_200_000));
        assertThat(result.remainingAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }
}

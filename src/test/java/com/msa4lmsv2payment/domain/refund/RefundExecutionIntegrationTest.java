package com.msa4lmsv2payment.domain.refund;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.PgCancelRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.RefundExecuteRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossRefundReceiveAccount;
import com.msa4lmsv2payment.global.error.TossPaymentRejectedException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-179/180 - 환불 실행(토스 실제 취소 API 연동)을 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RefundExecutionIntegrationTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private RefundService refundService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void pgCancelRefundSucceedsAndRecalculatesTuitionBillStatus() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(50L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-card-1");
        payment = paymentRepository.save(payment);

        RefundResponseDTO created = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), null, "단순변심"));
        assertThat(created.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(created.amount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));

        when(tossPaymentsClient.cancelPayment(eq("pk-card-1"), eq("단순변심"), eq(BigDecimal.valueOf(1_000_000)), isNull(), any()))
                .thenReturn(new TossPaymentResponse("pk-card-1", "PAYMENT-" + payment.getId(), "CANCELED", 1_000_000L));

        RefundResponseDTO executed = refundService.executeRefund(ADMIN, created.id(),
                new RefundExecuteRequestDTO("단순변심", null, null, null), "idem-pg-cancel-1");

        assertThat(executed.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.UNPAID);
    }

    @Test
    void pgCancelRefundExecutionFailureMarksRefundFailed() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(51L, 1L, BigDecimal.valueOf(500_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(500_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-card-2");
        payment = paymentRepository.save(payment);

        RefundResponseDTO created = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), null, "결제 오류"));

        when(tossPaymentsClient.cancelPayment(eq("pk-card-2"), any(), any(), isNull(), any()))
                .thenThrow(new TossPaymentRejectedException("토스 거부"));

        RefundResponseDTO executed = refundService.executeRefund(ADMIN, created.id(),
                new RefundExecuteRequestDTO("결제 오류", null, null, null), "idem-pg-cancel-2");

        assertThat(executed.status()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void withdrawalRefundExecutionUsesVirtualAccountPaymentKeyAndReceiveAccount() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(52L, 1L, BigDecimal.valueOf(2_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.PAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-WD-1", "secret-wd", "110-5555", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.DEPOSITED));
        account.assignPaymentKey("pk-va-issue-1");
        virtualAccountRepository.save(account);

        Refund refund = new Refund(bill.getId(), RefundType.WITHDRAWAL, BigDecimal.valueOf(1_666_600), BigDecimal.valueOf(0.8333), RefundStatus.REQUESTED);
        refund.linkVirtualAccount(account.getId());
        refund = refundRepository.save(refund);

        when(tossPaymentsClient.cancelPayment(eq("pk-va-issue-1"), any(), eq(BigDecimal.valueOf(1_666_600)),
                eq(new TossRefundReceiveAccount("020", "110123456789", "홍길동")), any()))
                .thenReturn(new TossPaymentResponse("pk-va-issue-1", account.getOrderId(), "CANCELED", 1_666_600L));

        RefundResponseDTO executed = refundService.executeRefund(ADMIN, refund.getId(),
                new RefundExecuteRequestDTO("자퇴 환불", "020", "110123456789", "홍길동"), "idem-withdrawal-1");

        assertThat(executed.status()).isEqualTo(RefundStatus.SUCCEEDED);
        verify(tossPaymentsClient).cancelPayment(eq("pk-va-issue-1"), any(), eq(BigDecimal.valueOf(1_666_600)),
                eq(new TossRefundReceiveAccount("020", "110123456789", "홍길동")), any());
    }

    @Test
    void withdrawalAndPgCancelRefundsCoexistOnTheSameTuitionBill() {
        // 유형별 유일성 범위가 다르다는 스키마 수정(refund_dedup_key)을 검증한다 -
        // 같은 고지에 WITHDRAWAL 환불 하나와 PG_CANCEL 환불 하나가 동시에 존재할 수 있어야 한다.
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(53L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(400_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-card-3");
        payment = paymentRepository.save(payment);

        Refund withdrawalRefund = refundRepository.save(
                new Refund(bill.getId(), RefundType.WITHDRAWAL, BigDecimal.valueOf(300_000), BigDecimal.valueOf(0.5), RefundStatus.REQUESTED));
        RefundResponseDTO pgCancelRefund = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), null, "부분 환불"));

        assertThat(refundRepository.findById(withdrawalRefund.getId())).isPresent();
        assertThat(refundRepository.findById(pgCancelRefund.id())).isPresent();
        assertThat(refundRepository.findByTuitionBillIdOrderByRequestedAtDesc(bill.getId())).hasSize(2);
    }

    @Test
    void 카드결제_부분취소는_지정한_금액만_취소하고_전액취소와_구분된다() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(54L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-card-partial-1");
        payment = paymentRepository.save(payment);

        RefundResponseDTO created = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), BigDecimal.valueOf(300_000), "일부 항목 환불"));
        assertThat(created.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(created.amount()).isEqualByComparingTo(BigDecimal.valueOf(300_000));

        when(tossPaymentsClient.cancelPayment(eq("pk-card-partial-1"), eq("일부 항목 환불"), eq(BigDecimal.valueOf(300_000)), isNull(), any()))
                .thenReturn(new TossPaymentResponse("pk-card-partial-1", "PAYMENT-" + payment.getId(), "CANCELED", 300_000L));

        RefundResponseDTO executed = refundService.executeRefund(ADMIN, created.id(),
                new RefundExecuteRequestDTO("일부 항목 환불", null, null, null), "idem-pg-cancel-partial-1");

        assertThat(executed.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(executed.amount()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        verify(tossPaymentsClient).cancelPayment(eq("pk-card-partial-1"), any(), eq(BigDecimal.valueOf(300_000)), isNull(), any());
    }

    @Test
    void 같은_결제에_취소를_재요청하면_새로_만들지_않고_기존_REQUESTED_건의_금액만_갱신한다() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(55L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-card-requeue-1");
        payment = paymentRepository.save(payment);

        RefundResponseDTO first = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), BigDecimal.valueOf(200_000), "1차 요청"));
        RefundResponseDTO second = refundService.createPgCancelRefund(ADMIN,
                new PgCancelRefundRequestDTO(payment.getId(), BigDecimal.valueOf(500_000), "2차 요청으로 금액 정정"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.amount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
        assertThat(refundRepository.findByTuitionBillIdOrderByRequestedAtDesc(bill.getId())).hasSize(1);
    }
}

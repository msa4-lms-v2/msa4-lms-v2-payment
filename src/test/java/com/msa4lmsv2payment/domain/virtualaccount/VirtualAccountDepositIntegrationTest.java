package com.msa4lmsv2payment.domain.virtualaccount;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountDepositRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.TossVirtualAccountDepositWebhookRequest;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountDepositService;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-126/128/129 - 가상계좌 입금 Webhook의 중복·부분·초과입금 처리를 실제 MySQL 컨테이너로 검증한다.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VirtualAccountDepositIntegrationTest {

    private static final String WEBHOOK_URL = "/api/payment/webhooks/toss/virtual-account-deposits";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VirtualAccountDepositService virtualAccountDepositService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private VirtualAccountDepositRepository virtualAccountDepositRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void malformedWebhookPayloadReturns400() throws Exception {
        String body = """
                {"secret": null, "status": "DONE", "transactionKey": null, "orderId": null, "createdAt": "2026-09-07T10:00:00"}
                """;

        mockMvc.perform(post(WEBHOOK_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateTransactionKeyIsProcessedOnlyOnce() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(1L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret-1", "110-1234-5678", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.ISSUED));

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-1", account.getOrderId(), "DONE", 1_000_000L));

        TossVirtualAccountDepositWebhookRequest webhook = new TossVirtualAccountDepositWebhookRequest(
                "secret-1", "DONE", "tx-duplicate-1", account.getOrderId(), "2026-09-07T10:00:00");

        String transmissionTime = java.time.Instant.now().toString();
        virtualAccountDepositService.processDeposit("event-duplicate-1", transmissionTime, webhook);
        virtualAccountDepositService.processDeposit("event-duplicate-1", transmissionTime, webhook); // 동일 이벤트 재전송

        assertThat(virtualAccountDepositRepository.findByVirtualAccountId(account.getId())).hasSize(1);
        assertThat(paymentRepository.findByTuitionBillIdAndStatus(bill.getId(), PaymentStatus.SUCCEEDED)).hasSize(1);
    }

    @Test
    void partialDepositThenCompletionMarksBillPaid() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(2L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret-2", "110-1234-5679", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.ISSUED));

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-2a", account.getOrderId(), "DONE", 400_000L))
                .thenReturn(new TossPaymentResponse("pk-2b", account.getOrderId(), "DONE", 600_000L));

        virtualAccountDepositService.processDeposit("event-partial-1", java.time.Instant.now().toString(), new TossVirtualAccountDepositWebhookRequest(
                "secret-2", "DONE", "tx-partial-1", account.getOrderId(), "2026-09-07T10:00:00"));

        VirtualAccount afterPartial = virtualAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(afterPartial.getStatus()).isEqualTo(VirtualAccountStatus.PARTIALLY_DEPOSITED);
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.UNPAID);

        virtualAccountDepositService.processDeposit("event-partial-2", java.time.Instant.now().toString(), new TossVirtualAccountDepositWebhookRequest(
                "secret-2", "DONE", "tx-partial-2", account.getOrderId(), "2026-09-07T10:05:00"));

        VirtualAccount afterFull = virtualAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(afterFull.getStatus()).isEqualTo(VirtualAccountStatus.DEPOSITED);
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.PAID);
        assertThat(paymentRepository.findByTuitionBillIdAndStatus(bill.getId(), PaymentStatus.SUCCEEDED)).hasSize(1);
    }

    @Test
    void overpaymentCreatesExcessDepositRefund() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(3L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret-3", "110-1234-5680", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.ISSUED));

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-3", account.getOrderId(), "DONE", 1_200_000L));

        virtualAccountDepositService.processDeposit("event-overpay-1", java.time.Instant.now().toString(), new TossVirtualAccountDepositWebhookRequest(
                "secret-3", "DONE", "tx-overpay-1", account.getOrderId(), "2026-09-07T10:00:00"));

        assertThat(refundRepository.findByTuitionBillIdAndRefundType(bill.getId(), RefundType.EXCESS_DEPOSIT))
                .hasValueSatisfying(refund -> {
                    assertThat(refund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
                    assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
                    assertThat(refund.getVirtualAccountId()).isEqualTo(account.getId());
                });
    }
}

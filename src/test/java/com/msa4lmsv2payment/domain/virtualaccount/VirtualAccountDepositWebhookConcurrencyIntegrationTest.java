package com.msa4lmsv2payment.domain.virtualaccount;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCRUM-71 - 가상계좌 입금 Webhook이 진짜 동시에(순차 재전송이 아니라) 중복 수신됐을 때
 * processDeposit()의 existsBy 사전 체크와 DB UNIQUE 제약 + DataIntegrityViolationException 처리가
 * 실제로 입금을 한 번만 기록하는지 검증한다. VirtualAccountDepositIntegrationTest의
 * duplicateTransactionKeyIsProcessedOnlyOnce()는 순차 재전송만 다뤄 이 경합 창을 검증하지 않는다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class VirtualAccountDepositWebhookConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

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

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void concurrentDuplicateWebhookDeliveriesRecordDepositOnlyOnce() throws Exception {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(9001L, 9001L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret-race", "110-1234-9001", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.ISSUED));

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-race", account.getOrderId(), "DONE", 1_000_000L));

        TossVirtualAccountDepositWebhookRequest webhook = new TossVirtualAccountDepositWebhookRequest(
                "secret-race", "DONE", "tx-race-1", account.getOrderId(), "2026-09-07T10:00:00");
        String transmissionTime = Instant.now().toString();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> { ready.countDown(); awaitUnchecked(start); deliver("event-race-1", transmissionTime, webhook); });
            Future<?> second = pool.submit(() -> { ready.countDown(); awaitUnchecked(start); deliver("event-race-1", transmissionTime, webhook); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            // 두 호출 모두 예외 없이 정상 반환돼야 한다 - 패자는 DataIntegrityViolationException을 내부에서 흡수한다.
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        assertThat(virtualAccountDepositRepository.findByVirtualAccountId(account.getId())).hasSize(1);
        assertThat(paymentRepository.findByTuitionBillIdAndStatus(bill.getId(), PaymentStatus.SUCCEEDED)).hasSize(1);
        assertThat(virtualAccountRepository.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountStatus.DEPOSITED);
    }

    private void deliver(String transmissionId, String transmissionTime, TossVirtualAccountDepositWebhookRequest webhook) {
        virtualAccountDepositService.processDeposit(transmissionId, transmissionTime, webhook);
    }

    private void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

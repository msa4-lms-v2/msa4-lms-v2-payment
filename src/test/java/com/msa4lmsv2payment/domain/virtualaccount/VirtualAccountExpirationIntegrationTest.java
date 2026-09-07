package com.msa4lmsv2payment.domain.virtualaccount;

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
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountExpirationScheduler;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCRUM-127 - 가상계좌 만료 스케줄러와 만료 후 입금 정책을 실제 MySQL 컨테이너로 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class VirtualAccountExpirationIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private VirtualAccountExpirationScheduler scheduler;

    @Autowired
    private VirtualAccountDepositService virtualAccountDepositService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private VirtualAccountDepositRepository virtualAccountDepositRepository;

    @Autowired
    private RefundRepository refundRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void expiresOverdueIssuedAndPartiallyDepositedAccountsButNotDeposited() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(10L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));

        VirtualAccount issuedOverdue = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret", "110-1", "020",
                LocalDateTime.now().minusHours(1), VirtualAccountStatus.ISSUED));
        VirtualAccount depositedOverdue = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret", "110-2", "020",
                LocalDateTime.now().minusHours(1), VirtualAccountStatus.DEPOSITED));
        VirtualAccount notYetDue = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret", "110-3", "020",
                LocalDateTime.now().plusDays(7), VirtualAccountStatus.ISSUED));

        scheduler.expireOverdueAccounts();

        assertThat(virtualAccountRepository.findById(issuedOverdue.getId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountStatus.EXPIRED);
        assertThat(virtualAccountRepository.findById(depositedOverdue.getId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountStatus.DEPOSITED);
        assertThat(virtualAccountRepository.findById(notYetDue.getId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountStatus.ISSUED);
    }

    @Test
    void depositToExpiredAccountRecordsDepositAndCreatesFullRefund() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(11L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        VirtualAccount account = virtualAccountRepository.save(new VirtualAccount(
                bill.getId(), "ORDER-" + UUID.randomUUID(), "secret-expired", "110-4", "020",
                LocalDateTime.now().minusHours(1), VirtualAccountStatus.ISSUED));
        scheduler.expireOverdueAccounts();
        assertThat(virtualAccountRepository.findById(account.getId()).orElseThrow().getStatus())
                .isEqualTo(VirtualAccountStatus.EXPIRED);

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-expired", account.getOrderId(), "DONE", 500_000L));

        virtualAccountDepositService.processDeposit(new TossVirtualAccountDepositWebhookRequest(
                "secret-expired", "DONE", "tx-expired-1", account.getOrderId(), "2026-09-07T10:00:00"));

        assertThat(virtualAccountDepositRepository.findByVirtualAccountId(account.getId())).hasSize(1);
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.UNPAID);
        assertThat(refundRepository.findByTuitionBillIdAndRefundType(bill.getId(), RefundType.EXCESS_DEPOSIT))
                .hasValueSatisfying(refund -> {
                    assertThat(refund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
                    assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
                    assertThat(refund.getVirtualAccountId()).isEqualTo(account.getId());
                });
    }
}

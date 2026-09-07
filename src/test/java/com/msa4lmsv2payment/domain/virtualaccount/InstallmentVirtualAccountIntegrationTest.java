package com.msa4lmsv2payment.domain.virtualaccount;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.TossVirtualAccountDepositWebhookRequest;
import com.msa4lmsv2payment.domain.virtualaccount.request.VirtualAccountIssueRequestDTO;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountDepositService;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossVirtualAccountIssueResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-131 잔여분 - 분할납부 회차별 가상계좌 발급·입금이 해당 회차만 완납 처리하는지 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class InstallmentVirtualAccountIntegrationTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private VirtualAccountService virtualAccountService;

    @Autowired
    private VirtualAccountDepositService virtualAccountDepositService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @Autowired
    private InstallmentPlanItemRepository installmentPlanItemRepository;

    @Autowired
    private VirtualAccountRepository virtualAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void depositToInstallmentScopedAccountPaysOnlyThatRoundNotTheWholeBill() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(40L, 1L, BigDecimal.valueOf(900_000), LocalDate.now().plusDays(60), TuitionBillStatus.UNPAID, 1L));
        InstallmentPlan plan = installmentPlanRepository.save(new InstallmentPlan(bill.getId(), 2));
        plan.approve(1L);
        installmentPlanRepository.save(plan);

        InstallmentPlanItem round1 = installmentPlanItemRepository.save(
                new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(450_000), LocalDate.now().plusDays(10)));
        InstallmentPlanItem round2 = installmentPlanItemRepository.save(
                new InstallmentPlanItem(plan.getId(), 2, BigDecimal.valueOf(450_000), LocalDate.now().plusDays(40)));

        when(tossPaymentsClient.issueVirtualAccount(any(), any(), eq(BigDecimal.valueOf(450_000)), any(), any()))
                .thenReturn(new TossVirtualAccountIssueResponse("pk-round1-issue", "secret-round1",
                        new TossVirtualAccountIssueResponse.VirtualAccountInfo("110-9999", "020", null)));

        virtualAccountService.issueVirtualAccount(ADMIN,
                new VirtualAccountIssueRequestDTO(bill.getId(), "020", "홍길동", round1.getId()));

        verify(tossPaymentsClient).issueVirtualAccount(any(), any(), eq(BigDecimal.valueOf(450_000)), any(), any());

        VirtualAccount account = virtualAccountRepository.findByInstallmentPlanItemId(round1.getId()).orElseThrow();

        when(tossPaymentsClient.getPaymentByOrderId(eq(account.getOrderId())))
                .thenReturn(new TossPaymentResponse("pk-round1", account.getOrderId(), "DONE", 450_000L));

        virtualAccountDepositService.processDeposit("event-round1", java.time.Instant.now().toString(), new TossVirtualAccountDepositWebhookRequest(
                "secret-round1", "DONE", "tx-round1", account.getOrderId(), "2026-09-07T10:00:00"));

        assertThat(installmentPlanItemRepository.findById(round1.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentItemStatus.PAID);
        assertThat(installmentPlanItemRepository.findById(round2.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentItemStatus.SCHEDULED);
        assertThat(installmentPlanRepository.findById(plan.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentPlanStatus.ACTIVE); // 2회차가 아직 남아 COMPLETED 아님

        // 회차 스코프 입금은 고지 전체 상태를 건드리지 않는다 - 고지 잔액은 payment-status API로 별도 재계산한다.
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.UNPAID);

        assertThat(paymentRepository.findByTuitionBillIdAndStatus(bill.getId(), PaymentStatus.SUCCEEDED))
                .hasSize(1)
                .allSatisfy(payment -> assertThat(payment.getInstallmentPlanItemId()).isEqualTo(round1.getId()));
    }
}

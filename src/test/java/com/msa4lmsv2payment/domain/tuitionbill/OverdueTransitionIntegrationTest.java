package com.msa4lmsv2payment.domain.tuitionbill;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.service.OverdueTransitionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-132 - 기한이 지난 등록금 고지·분할납부 회차의 OVERDUE 전환을 실제 MySQL 컨테이너로 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class OverdueTransitionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private OverdueTransitionScheduler scheduler;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @Autowired
    private InstallmentPlanItemRepository installmentPlanItemRepository;

    @Test
    void transitionsOverdueTuitionBillsButNotPaidOnes() {
        TuitionBill overdueUnpaid = tuitionBillRepository.save(
                new TuitionBill(20L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().minusDays(1), TuitionBillStatus.UNPAID, 1L));
        TuitionBill overduePaid = tuitionBillRepository.save(
                new TuitionBill(21L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().minusDays(1), TuitionBillStatus.PAID, 1L));
        TuitionBill notYetDue = tuitionBillRepository.save(
                new TuitionBill(22L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(10), TuitionBillStatus.UNPAID, 1L));

        scheduler.transitionOverdueTuitionBills();

        assertThat(tuitionBillRepository.findById(overdueUnpaid.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.OVERDUE);
        assertThat(tuitionBillRepository.findById(overduePaid.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.PAID);
        assertThat(tuitionBillRepository.findById(notYetDue.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.UNPAID);
    }

    @Test
    void transitionsOverdueInstallmentItemsButNotPaidOnes() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(23L, 1L, BigDecimal.valueOf(900_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        InstallmentPlan plan = installmentPlanRepository.save(new InstallmentPlan(bill.getId(), 3));

        InstallmentPlanItem overdueItem = installmentPlanItemRepository.save(
                new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(300_000), LocalDate.now().minusDays(1)));
        InstallmentPlanItem paidItem = installmentPlanItemRepository.save(
                new InstallmentPlanItem(plan.getId(), 2, BigDecimal.valueOf(300_000), LocalDate.now().minusDays(1)));
        paidItem.markPaid();
        installmentPlanItemRepository.save(paidItem);
        InstallmentPlanItem futureItem = installmentPlanItemRepository.save(
                new InstallmentPlanItem(plan.getId(), 3, BigDecimal.valueOf(300_000), LocalDate.now().plusMonths(1)));

        scheduler.transitionOverdueInstallmentItems();

        assertThat(installmentPlanItemRepository.findById(overdueItem.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentItemStatus.OVERDUE);
        assertThat(installmentPlanItemRepository.findById(paidItem.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentItemStatus.PAID);
        assertThat(installmentPlanItemRepository.findById(futureItem.getId()).orElseThrow().getStatus())
                .isEqualTo(InstallmentItemStatus.SCHEDULED);
    }
}

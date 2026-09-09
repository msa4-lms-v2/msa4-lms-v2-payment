package com.msa4lmsv2payment.domain.installment;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanCreateRequestDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanResponseDTO;
import com.msa4lmsv2payment.domain.installment.service.InstallmentPlanService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyExistsException;
import com.msa4lmsv2payment.global.security.CurrentUser;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-184 - 같은 등록금 고지에 분할납부 신청 2건이 진짜 동시에 들어와도(check-then-act 사이의 경합 창)
 * 계획이 하나만 생성되고, 나머지 하나는 uk_installment_plans_tuition_bill_id UNIQUE 제약을 거쳐
 * InstallmentPlanAlreadyExistsException(명확한 사유)으로 반려되는지 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class InstallmentPlanConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private InstallmentPlanService installmentPlanService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @Test
    void concurrentApplicationsOnlyOnePlanIsCreated() throws Exception {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(9101L, 9101L, BigDecimal.valueOf(1_200_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        InstallmentPlanCreateRequestDTO request = new InstallmentPlanCreateRequestDTO(bill.getId(), 3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Object[] outcomes = new Object[2];
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> outcomes[0] = attempt(request, admin, ready, start));
            Future<?> second = pool.submit(() -> outcomes[1] = attempt(request, admin, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }

        long successCount = List.of(outcomes).stream().filter(InstallmentPlanResponseDTO.class::isInstance).count();
        long conflictCount = List.of(outcomes).stream().filter(InstallmentPlanAlreadyExistsException.class::isInstance).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        List<InstallmentPlan> plans = installmentPlanRepository.findByTuitionBillId(bill.getId()).map(List::of).orElse(List.of());
        assertThat(plans).hasSize(1);
    }

    private Object attempt(InstallmentPlanCreateRequestDTO request, CurrentUser admin, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        try {
            return installmentPlanService.createPlan(admin, request);
        } catch (InstallmentPlanAlreadyExistsException conflict) {
            return conflict;
        }
    }
}

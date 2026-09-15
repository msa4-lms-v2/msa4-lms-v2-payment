package com.msa4lmsv2payment.domain.installment;

import com.msa4lmsv2payment.domain.installment.entity.*;
import com.msa4lmsv2payment.domain.installment.repository.*;
import com.msa4lmsv2payment.domain.installment.request.*;
import com.msa4lmsv2payment.domain.installment.service.InstallmentPlanService;
import com.msa4lmsv2payment.domain.payment.entity.*;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.*;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.service.OverdueTransitionScheduler;
import com.msa4lmsv2payment.global.audit.*;
import com.msa4lmsv2payment.global.error.*;
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
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class InstallmentAutoApprovalIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    @Autowired InstallmentPlanService service;
    @Autowired TuitionBillRepository bills;
    @Autowired InstallmentPlanRepository plans;
    @Autowired InstallmentPlanItemRepository items;
    @Autowired PaymentRepository payments;
    @Autowired AuditLogRepository audit;
    @Autowired InstallmentEligibilityRepository eligibility;
    @Autowired OverdueTransitionScheduler scheduler;
    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");
    private static final AtomicLong IDS = new AtomicLong(85000);

    private TuitionBill bill(long student, LocalDate date, TuitionBillStatus status) {
        return bills.save(new TuitionBill(student, IDS.incrementAndGet(), BigDecimal.valueOf(1000000), date, status, 1L));
    }
    private TuitionBill current(long student) { return bill(student, LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID); }
    private InstallmentPlanCreateRequestDTO request(TuitionBill bill) { return new InstallmentPlanCreateRequestDTO(bill.getId(), 3); }

    @Test void 연체가_없으면_미리보기와_자동승인_금액이_같고_회차결제가_가능하다() {
        var bill = current(IDS.incrementAndGet());
        var preview = service.preview(ADMIN, request(bill));
        assertThat(preview.requiresReview()).isFalse();
        assertThat(plans.findByTuitionBillId(bill.getId())).isEmpty();
        var plan = service.createPlan(ADMIN, request(bill));
        assertThat(plan.status()).isEqualTo(InstallmentPlanStatus.ACTIVE);
        assertThat(plan.reviewedBy()).isNull();
        assertThat(plan.reviewedAt()).isNotNull();
        assertThat(plan.items()).extracting(i -> i.amount().longValue()).containsExactly(333333L, 333333L, 333334L);
        assertThat(plan.items()).extracting(i -> i.dueDate()).containsExactly(
                bill.getDueDate(), bill.getDueDate().plusMonths(1), bill.getDueDate().plusMonths(2));
        assertThat(service.getItemOrThrow(bill.getId(), plan.items().getFirst().id())).isNotNull();
        assertThat(audit.findAll()).anyMatch(a -> a.getAction().equals("INSTALLMENT_PLAN_REVIEWED")
                && a.getTargetId().equals(plan.id()) && a.getActorId() == 0L);
        assertThatThrownBy(() -> service.createPlan(ADMIN, request(bill))).isInstanceOf(InstallmentPlanAlreadyExistsException.class);
    }

    @Test void 과거_연체가_있으면_관리자_승인_전_결제를_막는다() {
        long student = IDS.incrementAndGet();
        bill(student, LocalDate.now().minusDays(1), TuitionBillStatus.UNPAID);
        var bill = current(student);
        assertThat(service.preview(ADMIN, request(bill)).requiresReview()).isTrue();
        var plan = service.createPlan(ADMIN, request(bill));
        assertThat(plan.status()).isEqualTo(InstallmentPlanStatus.REQUESTED);
        assertThatThrownBy(() -> service.getItemOrThrow(bill.getId(), plan.items().getFirst().id()))
                .isInstanceOf(InstallmentPlanNotApprovedException.class);
        service.reviewPlan(ADMIN, plan.id(), new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.APPROVE, null));
        assertThat(service.getItemOrThrow(bill.getId(), plan.items().getFirst().id())).isNotNull();
    }

    @Test void 완납한_고지의_연체로그도_이력으로_인정한다() {
        long student = IDS.incrementAndGet();
        var old = bill(student, LocalDate.now().minusDays(30), TuitionBillStatus.PAID);
        audit.save(new AuditLog(0L, "TUITION_BILL_OVERDUE", "TUITION_BILL", old.getId(), "{}", null));
        assertThat(service.createPlan(ADMIN, request(current(student))).status()).isEqualTo(InstallmentPlanStatus.REQUESTED);
    }

    @Test void 스케줄러_기록이_없어도_늦게_납부한_결제로_이력을_판단한다() {
        long student = IDS.incrementAndGet();
        var old = bill(student, LocalDate.now().minusDays(2), TuitionBillStatus.PAID);
        var payment = new Payment(old.getId(), student, old.getBillingAmount(), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("late-" + student);
        payments.save(payment);
        assertThat(eligibility.hasDelinquency(student, LocalDate.now())).isTrue();
    }

    @Test void 기한_당일_납부는_연체가_아니다() {
        long student = IDS.incrementAndGet();
        var old = bill(student, LocalDate.now(), TuitionBillStatus.PAID);
        var payment = new Payment(old.getId(), student, old.getBillingAmount(), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("ontime-" + student);
        payments.save(payment);
        assertThat(eligibility.hasDelinquency(student, LocalDate.now())).isFalse();
    }

    @Test void 분할납부중에는_원고지_기한으로_연체처리하지_않는다() {
        long student = IDS.incrementAndGet();
        var old = bill(student, LocalDate.now().minusDays(2), TuitionBillStatus.PARTIAL);
        var plan = new InstallmentPlan(old.getId(), 2);
        plan.approveAutomatically();
        plan = plans.save(plan);
        var paid = new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(500000), old.getDueDate());
        paid.markPaid(); items.save(paid);
        items.save(new InstallmentPlanItem(plan.getId(), 2, BigDecimal.valueOf(500000), LocalDate.now().plusDays(20)));
        scheduler.transitionOverdueTuitionBills();
        assertThat(bills.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.PARTIAL);
        assertThat(eligibility.hasDelinquency(student, LocalDate.now())).isFalse();
    }

    @Test void 완납된_회차의_연체_이력은_유지된다() {
        long student = IDS.incrementAndGet();
        var old = bill(student, LocalDate.now().minusDays(2), TuitionBillStatus.PAID);
        var plan = new InstallmentPlan(old.getId(), 2); plan.approveAutomatically(); plan.complete(); plan = plans.save(plan);
        var paid = new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(500000), old.getDueDate());
        paid.markPaid(); paid = items.save(paid);
        audit.save(new AuditLog(0L, "INSTALLMENT_ITEM_OVERDUE", "INSTALLMENT_PLAN_ITEM", paid.getId(), "{}", null));
        assertThat(eligibility.hasDelinquency(student, LocalDate.now())).isTrue();
    }

    @Test void 신청후_남은_연체회차가_있으면_완료처리하지_않는다() {
        long student = IDS.incrementAndGet(); var bill = current(student);
        var response = service.createPlan(ADMIN, request(bill));
        var overdue = items.findById(response.items().get(1).id()).orElseThrow(); overdue.markOverdue(); items.save(overdue);
        var last = items.findById(response.items().get(2).id()).orElseThrow(); last.markPaid(); items.save(last);
        var payment = payments.save(new Payment(bill.getId(), student, BigDecimal.valueOf(333333), PaymentMethod.CARD, PaymentStatus.REQUESTED));
        service.assignPaymentToItem(response.items().getFirst().id(), payment.getId());
        service.markItemPaid(response.items().getFirst().id(), payment.getId());
        assertThat(plans.findById(response.id()).orElseThrow().getStatus()).isEqualTo(InstallmentPlanStatus.ACTIVE);
    }

    @Test void 부분납부_완납_기한초과_고지는_신청할수없다() {
        for (var status : new TuitionBillStatus[]{TuitionBillStatus.PAID, TuitionBillStatus.PARTIAL, TuitionBillStatus.OVERDUE}) {
            var bill = bill(IDS.incrementAndGet(), LocalDate.now().plusDays(1), status);
            assertThatThrownBy(() -> service.createPlan(ADMIN, request(bill))).isInstanceOf(InstallmentApplicationNotAllowedException.class);
        }
        var expired = bill(IDS.incrementAndGet(), LocalDate.now().minusDays(1), TuitionBillStatus.UNPAID);
        assertThatThrownBy(() -> service.preview(ADMIN, request(expired))).isInstanceOf(InstallmentApplicationNotAllowedException.class);
    }

    @Test void 결제진행중인_고지는_신청할수없다() {
        var bill = current(IDS.incrementAndGet());
        payments.save(new Payment(bill.getId(), bill.getStudentId(), bill.getBillingAmount(), PaymentMethod.CARD, PaymentStatus.REQUESTED));
        assertThatThrownBy(() -> service.createPlan(ADMIN, request(bill))).isInstanceOf(InstallmentApplicationNotAllowedException.class);
        assertThat(plans.findByTuitionBillId(bill.getId())).isEmpty();
    }
}

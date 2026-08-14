package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanCreateRequestDTO;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanDecision;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanReviewRequestDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.InstallmentItemAlreadyPaidException;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyExistsException;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyReviewedException;
import com.msa4lmsv2payment.global.error.InstallmentPlanNotApprovedException;
import com.msa4lmsv2payment.global.error.InstallmentPlanNotFoundException;
import com.msa4lmsv2payment.global.error.RejectReasonRequiredException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentPlanServiceTest {

    @Mock
    private InstallmentPlanRepository installmentPlanRepository;
    @Mock
    private InstallmentPlanItemRepository installmentPlanItemRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private ScholarshipService scholarshipService;
    @Mock
    private InstallmentPlanRecorder installmentPlanRecorder;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    @InjectMocks
    private InstallmentPlanService installmentPlanService;

    private static TuitionBill tuitionBill(Long id, BigDecimal billingAmount, LocalDate dueDate) {
        TuitionBill bill = new TuitionBill(20260001L, 5L, billingAmount, dueDate, TuitionBillStatus.UNPAID, 1L);
        setField(TuitionBill.class, bill, "id", id);
        return bill;
    }

    private static InstallmentPlan plan(Long id, Long tuitionBillId, int totalRounds) {
        InstallmentPlan plan = new InstallmentPlan(tuitionBillId, totalRounds);
        setField(InstallmentPlan.class, plan, "id", id);
        return plan;
    }

    private static InstallmentPlan activePlan(Long id, Long tuitionBillId, int totalRounds) {
        InstallmentPlan plan = plan(id, tuitionBillId, totalRounds);
        setField(InstallmentPlan.class, plan, "status", InstallmentPlanStatus.ACTIVE);
        return plan;
    }

    private static InstallmentPlanItem item(Long id, Long planId, int roundNo, BigDecimal amount, InstallmentItemStatus status, Long paymentId) {
        InstallmentPlanItem item = new InstallmentPlanItem(planId, roundNo, amount, LocalDate.of(2026, 8, 25).plusMonths(roundNo - 1));
        setField(InstallmentPlanItem.class, item, "id", id);
        setField(InstallmentPlanItem.class, item, "status", status);
        setField(InstallmentPlanItem.class, item, "paymentId", paymentId);
        return item;
    }

    private static <T> void setField(Class<T> type, T target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 분할납부_계획_생성시_실납부액을_회차수로_나누고_나머지는_마지막_회차에_더한다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000), LocalDate.of(2026, 8, 25));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.empty());
        when(scholarshipService.calculateAllocation(any(), any()))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(4_200_000), BigDecimal.ZERO, BigDecimal.valueOf(1_000_000)));

        InstallmentPlan savedPlan = plan(10L, 1L, 3);
        when(installmentPlanRecorder.saveWithAudit(org.mockito.ArgumentMatchers.eq(1L), any(), any())).thenReturn(savedPlan);
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(10L)).thenReturn(List.of(
                item(101L, 10L, 1, new BigDecimal("333333"), InstallmentItemStatus.SCHEDULED, null),
                item(102L, 10L, 2, new BigDecimal("333333"), InstallmentItemStatus.SCHEDULED, null),
                item(103L, 10L, 3, new BigDecimal("333334"), InstallmentItemStatus.SCHEDULED, null)
        ));

        InstallmentPlanResponseDTO result = installmentPlanService.createPlan(student, new InstallmentPlanCreateRequestDTO(1L, 3));

        assertThat(result.items()).hasSize(3);
        BigDecimal total = result.items().stream().map(i -> i.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    void 이미_계획이_있으면_거부된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000), LocalDate.of(2026, 8, 25));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(plan(10L, 1L, 3)));

        assertThatThrownBy(() -> installmentPlanService.createPlan(student, new InstallmentPlanCreateRequestDTO(1L, 3)))
                .isInstanceOf(InstallmentPlanAlreadyExistsException.class);
    }

    @Test
    void 계획이_없으면_조회시_404() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000), LocalDate.of(2026, 8, 25));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> installmentPlanService.getPlan(student, 1L))
                .isInstanceOf(InstallmentPlanNotFoundException.class);
    }

    @Test
    void 이미_결제완료된_회차는_다시_조회해서_결제할_수_없다() {
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(activePlan(10L, 1L, 3)));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(10L)).thenReturn(List.of(
                item(101L, 10L, 1, new BigDecimal("333333"), InstallmentItemStatus.PAID, 500L)
        ));

        assertThatThrownBy(() -> installmentPlanService.getItemOrThrow(1L, 101L))
                .isInstanceOf(InstallmentItemAlreadyPaidException.class);
    }

    @Test
    void 아직_승인되지_않은_계획은_회차_결제를_시작할_수_없다() {
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(plan(10L, 1L, 3)));

        assertThatThrownBy(() -> installmentPlanService.getItemOrThrow(1L, 101L))
                .isInstanceOf(InstallmentPlanNotApprovedException.class);
    }

    @Test
    void 관리자가_승인하면_계획이_ACTIVE로_바뀐다() {
        CurrentUser admin = new CurrentUser(2L, "ADMIN");
        InstallmentPlan targetPlan = plan(10L, 1L, 3);
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(targetPlan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(10L)).thenReturn(List.of());

        installmentPlanService.reviewPlan(admin, 10L, new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.APPROVE, null));

        assertThat(targetPlan.getStatus()).isEqualTo(InstallmentPlanStatus.ACTIVE);
        assertThat(targetPlan.getReviewedBy()).isEqualTo(2L);
    }

    @Test
    void 반려시_사유가_없으면_거부된다() {
        CurrentUser admin = new CurrentUser(2L, "ADMIN");
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(plan(10L, 1L, 3)));

        assertThatThrownBy(() -> installmentPlanService.reviewPlan(admin, 10L,
                new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.REJECT, null)))
                .isInstanceOf(RejectReasonRequiredException.class);
    }

    @Test
    void 이미_심사완료된_신청은_다시_심사할_수_없다() {
        CurrentUser admin = new CurrentUser(2L, "ADMIN");
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(activePlan(10L, 1L, 3)));

        assertThatThrownBy(() -> installmentPlanService.reviewPlan(admin, 10L,
                new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.APPROVE, null)))
                .isInstanceOf(InstallmentPlanAlreadyReviewedException.class);
    }

    @Test
    void 마지막_회차까지_납부완료되면_계획이_완료상태가_된다() {
        InstallmentPlanItem targetItem = item(103L, 10L, 3, new BigDecimal("333334"), InstallmentItemStatus.SCHEDULED, 900L);
        when(installmentPlanItemRepository.findById(103L)).thenReturn(Optional.of(targetItem));
        when(installmentPlanItemRepository.countByInstallmentPlanIdAndStatus(10L, InstallmentItemStatus.SCHEDULED)).thenReturn(0L);
        InstallmentPlan targetPlan = plan(10L, 1L, 3);
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(targetPlan));

        installmentPlanService.markItemPaid(103L, 900L);

        assertThat(targetItem.getStatus()).isEqualTo(InstallmentItemStatus.PAID);
        assertThat(targetPlan.getStatus().name()).isEqualTo("COMPLETED");
    }

    @Test
    void 결제ID가_일치하지_않으면_회차를_변경하지_않는다() {
        InstallmentPlanItem targetItem = item(101L, 10L, 1, new BigDecimal("333333"), InstallmentItemStatus.SCHEDULED, 500L);
        when(installmentPlanItemRepository.findById(101L)).thenReturn(Optional.of(targetItem));

        installmentPlanService.markItemPaid(101L, 999L);

        assertThat(targetItem.getStatus()).isEqualTo(InstallmentItemStatus.SCHEDULED);
    }

    @Test
    void installmentPlanItemId가_null이면_아무일도_하지_않는다() {
        installmentPlanService.markItemPaid(null, 1L);
        org.mockito.Mockito.verifyNoInteractions(installmentPlanItemRepository);
    }
}

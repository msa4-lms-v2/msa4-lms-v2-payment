package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanCreateRequestDTO;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanDecision;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanReviewRequestDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyExistsException;
import com.msa4lmsv2payment.global.error.RejectReasonRequiredException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstallmentPlanServiceTest {

    @Mock InstallmentPlanRepository installmentPlanRepository;
    @Mock InstallmentPlanItemRepository installmentPlanItemRepository;
    @Mock TuitionBillService tuitionBillService;
    @Mock ScholarshipService scholarshipService;
    @Mock InstallmentPlanRecorderService installmentPlanRecorder;
    @Mock AuditLogRecorder auditLogRecorder;
    @Mock TuitionBill tuitionBill;

    private static final CurrentUser STUDENT = new CurrentUser(1L, "STUDENT");
    private static final CurrentUser ADMIN = new CurrentUser(2L, "ADMIN");

    private InstallmentPlanService service;

    @BeforeEach
    void setUp() {
        service = new InstallmentPlanService(installmentPlanRepository, installmentPlanItemRepository,
                tuitionBillService, scholarshipService, installmentPlanRecorder, auditLogRecorder);
    }

    private InstallmentPlan activePlan() {
        InstallmentPlan plan = new InstallmentPlan(1L, 3);
        plan.approve(999L);
        return plan;
    }

    @Test
    void 분할납부_계획이_없으면_재계산하지_않는다() {
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.empty());

        service.recalculateForScholarshipChange(999L, 1L, BigDecimal.valueOf(900_000));

        verify(installmentPlanItemRepository, never()).findByInstallmentPlanIdOrderByRoundNo(any());
        verify(auditLogRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ACTIVE가_아닌_계획은_재계산하지_않는다() {
        InstallmentPlan requestedPlan = new InstallmentPlan(1L, 3);
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(requestedPlan));

        service.recalculateForScholarshipChange(999L, 1L, BigDecimal.valueOf(900_000));

        verify(installmentPlanItemRepository, never()).findByInstallmentPlanIdOrderByRoundNo(any());
    }

    @Test
    void 전_회차가_이미_납부됐으면_재계산하지_않는다() {
        InstallmentPlan plan = activePlan();
        InstallmentPlanItem item1 = new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(300_000), LocalDate.now());
        item1.markPaid();
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(plan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId())).thenReturn(List.of(item1));

        service.recalculateForScholarshipChange(999L, 1L, BigDecimal.valueOf(900_000));

        assertEquals(BigDecimal.valueOf(300_000), item1.getAmount());
        verify(auditLogRecorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 미납_회차만_새_실납부액_기준으로_재분배하고_이미_납부한_회차는_바꾸지_않는다() {
        InstallmentPlan plan = activePlan();
        InstallmentPlanItem paidItem = new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(300_000), LocalDate.now());
        paidItem.markPaid();
        InstallmentPlanItem scheduledItem = new InstallmentPlanItem(plan.getId(), 2, BigDecimal.valueOf(300_000), LocalDate.now());
        InstallmentPlanItem overdueItem = new InstallmentPlanItem(plan.getId(), 3, BigDecimal.valueOf(300_000), LocalDate.now());
        overdueItem.markOverdue();
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(plan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId()))
                .thenReturn(List.of(paidItem, scheduledItem, overdueItem));

        // 기존 실납부액 900,000(1~3회차 각 300,000) 중 장학금이 늘어 새 실납부액이 600,000이 됨.
        // 이미 낸 1회차(300,000)를 뺀 나머지 300,000을 남은 2개 회차(SCHEDULED, OVERDUE)에 다시 나눈다.
        service.recalculateForScholarshipChange(999L, 1L, BigDecimal.valueOf(600_000));

        assertEquals(BigDecimal.valueOf(300_000), paidItem.getAmount());
        assertEquals(BigDecimal.valueOf(150_000), scheduledItem.getAmount());
        assertEquals(BigDecimal.valueOf(150_000), overdueItem.getAmount());
        // plan은 저장되지 않은 상태(id=null)로 테스트하므로 targetId는 null 매칭만 확인한다.
        verify(auditLogRecorder).record(eq(999L), any(), eq("INSTALLMENT_PLAN"), isNull(), any(), isNull());
    }

    @Test
    void 새_실납부액이_이미_납부한_금액보다_작으면_남은_회차는_0원이_된다() {
        InstallmentPlan plan = activePlan();
        InstallmentPlanItem paidItem = new InstallmentPlanItem(plan.getId(), 1, BigDecimal.valueOf(300_000), LocalDate.now());
        paidItem.markPaid();
        InstallmentPlanItem scheduledItem = new InstallmentPlanItem(plan.getId(), 2, BigDecimal.valueOf(300_000), LocalDate.now());
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(plan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId()))
                .thenReturn(List.of(paidItem, scheduledItem));

        service.recalculateForScholarshipChange(999L, 1L, BigDecimal.valueOf(100_000));

        assertEquals(BigDecimal.ZERO.setScale(0), scheduledItem.getAmount().setScale(0));
    }

    @Test
    void 이미_계획이_있는_고지는_중복_신청을_거부한다() {
        when(tuitionBillService.getOwnedTuitionBillOrThrow(STUDENT, 1L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(1L);
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.of(new InstallmentPlan(1L, 3)));

        assertThrows(InstallmentPlanAlreadyExistsException.class,
                () -> service.createPlan(STUDENT, new InstallmentPlanCreateRequestDTO(1L, 3)));
        verify(installmentPlanRecorder, never()).saveWithAudit(any(), any(), any());
    }

    @Test
    void 신청시_회차금액은_실납부액을_회차수로_나눈_값이고_마지막_회차가_나머지를_흡수한다() {
        when(tuitionBillService.getOwnedTuitionBillOrThrow(STUDENT, 1L)).thenReturn(tuitionBill);
        when(tuitionBill.getId()).thenReturn(1L);
        when(tuitionBill.getDueDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(installmentPlanRepository.findByTuitionBillId(1L)).thenReturn(Optional.empty());
        when(scholarshipService.calculateAllocation(eq(STUDENT), any(PaymentScholarshipAllocationRequestDTO.class)))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(1L, BigDecimal.valueOf(1_000_000),
                        BigDecimal.ZERO, BigDecimal.valueOf(1_000_000)));
        InstallmentPlan savedPlan = new InstallmentPlan(1L, 3);
        when(installmentPlanRecorder.saveWithAudit(eq(STUDENT.id()), any(), any())).thenReturn(savedPlan);
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(savedPlan.getId())).thenReturn(List.of());

        service.createPlan(STUDENT, new InstallmentPlanCreateRequestDTO(1L, 3));

        var itemDraftsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(installmentPlanRecorder).saveWithAudit(eq(STUDENT.id()), any(), itemDraftsCaptor.capture());
        List<InstallmentPlanItemDraft> drafts = itemDraftsCaptor.getValue();
        assertEquals(3, drafts.size());
        assertEquals(BigDecimal.valueOf(333_333), drafts.get(0).amount());
        assertEquals(BigDecimal.valueOf(333_333), drafts.get(1).amount());
        assertEquals(BigDecimal.valueOf(333_334), drafts.get(2).amount());
    }

    @Test
    void ADMIN_승인시_계획이_ACTIVE로_전환된다() {
        InstallmentPlan plan = new InstallmentPlan(1L, 2);
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId())).thenReturn(List.of());

        InstallmentPlanResponseDTO response = service.reviewPlan(ADMIN, 10L,
                new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.APPROVE, null));

        assertEquals(InstallmentPlanStatus.ACTIVE, response.status());
    }

    @Test
    void 반려시_사유가_없으면_거부한다() {
        InstallmentPlan plan = new InstallmentPlan(1L, 2);
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(plan));

        assertThrows(RejectReasonRequiredException.class, () -> service.reviewPlan(ADMIN, 10L,
                new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.REJECT, null)));
    }

    @Test
    void 반려시_사유와_함께_REJECTED로_전환된다() {
        InstallmentPlan plan = new InstallmentPlan(1L, 2);
        when(installmentPlanRepository.findById(10L)).thenReturn(Optional.of(plan));
        when(installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId())).thenReturn(List.of());

        InstallmentPlanResponseDTO response = service.reviewPlan(ADMIN, 10L,
                new InstallmentPlanReviewRequestDTO(InstallmentPlanDecision.REJECT, "서류 미비"));

        assertEquals(InstallmentPlanStatus.REJECTED, response.status());
    }
}

package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 분할납부 계획과 회차 항목 저장, 감사 로그 기록을 하나의 트랜잭션으로 묶는다(TuitionBillRecorder와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class InstallmentPlanRecorder {

    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentPlanItemRepository installmentPlanItemRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public InstallmentPlan saveWithAudit(Long actorId, InstallmentPlan plan, List<InstallmentPlanItemDraft> itemDrafts) {
        InstallmentPlan savedPlan = installmentPlanRepository.save(plan);

        List<InstallmentPlanItem> items = itemDrafts.stream()
                .map(draft -> new InstallmentPlanItem(savedPlan.getId(), draft.roundNo(), draft.amount(), draft.dueDate()))
                .toList();
        installmentPlanItemRepository.saveAll(items);

        auditLogRecorder.record(actorId, AuditAction.INSTALLMENT_PLAN_REQUESTED, "INSTALLMENT_PLAN", savedPlan.getId(),
                Map.of("tuitionBillId", savedPlan.getTuitionBillId(), "totalRounds", savedPlan.getTotalRounds()), null);

        return savedPlan;
    }
}

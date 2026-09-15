package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.error.TuitionBillNotFoundException;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyExistsException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 분할납부 계획과 회차 항목 저장, 감사 로그 기록을 하나의 트랜잭션으로 묶는다(TuitionBillRecorderService와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class InstallmentPlanRecorderService {

    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentPlanItemRepository installmentPlanItemRepository;
    private final AuditLogRecorder auditLogRecorder;
    private final TuitionBillRepository tuitionBillRepository;
    private final InstallmentApplicationPolicy applicationPolicy;

    @Transactional
    public InstallmentPlan saveWithAudit(Long actorId, InstallmentPlan plan, List<InstallmentPlanItemDraft> itemDrafts) {
        var bill = tuitionBillRepository.findByIdForUpdate(plan.getTuitionBillId())
                .orElseThrow(() -> new TuitionBillNotFoundException("등록금 고지를 찾을 수 없습니다."));
        if (installmentPlanRepository.findByTuitionBillId(bill.getId()).isPresent()) {
            throw new InstallmentPlanAlreadyExistsException("이미 분할납부 계획이 존재하는 고지입니다.");
        }
        applicationPolicy.validate(bill, itemDrafts.stream().map(InstallmentPlanItemDraft::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), plan.getTotalRounds());
        boolean requiresReview = applicationPolicy.requiresReview(bill);
        if (!requiresReview) plan.approveAutomatically();
        InstallmentPlan savedPlan = installmentPlanRepository.save(plan);

        List<InstallmentPlanItem> items = itemDrafts.stream()
                .map(draft -> new InstallmentPlanItem(savedPlan.getId(), draft.roundNo(), draft.amount(), draft.dueDate()))
                .toList();
        installmentPlanItemRepository.saveAll(items);

        auditLogRecorder.record(actorId, AuditAction.INSTALLMENT_PLAN_REQUESTED, "INSTALLMENT_PLAN", savedPlan.getId(),
                Map.of("tuitionBillId", savedPlan.getTuitionBillId(), "totalRounds", savedPlan.getTotalRounds()), null);
        if (!requiresReview) {
            auditLogRecorder.record(0L, AuditAction.INSTALLMENT_PLAN_REVIEWED, "INSTALLMENT_PLAN", savedPlan.getId(),
                    Map.of("decision", "APPROVE", "automatic", true), "연체 이력 없음");
        }

        return savedPlan;
    }
}

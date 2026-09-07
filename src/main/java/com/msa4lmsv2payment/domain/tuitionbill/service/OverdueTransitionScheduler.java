package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 기한(due_date)이 지났는데 미납인 등록금 고지·분할납부 회차를 OVERDUE로 전환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueTransitionScheduler {

    private static final List<TuitionBillStatus> OVERDUE_ELIGIBLE_STATUSES =
            List.of(TuitionBillStatus.UNPAID, TuitionBillStatus.PARTIAL);
    // 스케줄러는 로그인 사용자가 없는 시스템 동작이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다.
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final TuitionBillRepository tuitionBillRepository;
    private final InstallmentPlanItemRepository installmentPlanItemRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void transitionOverdueTuitionBills() {
        List<TuitionBill> targets = tuitionBillRepository.findByStatusInAndDueDateBefore(
                OVERDUE_ELIGIBLE_STATUSES, LocalDate.now());
        for (TuitionBill tuitionBill : targets) {
            tuitionBill.changeStatus(TuitionBillStatus.OVERDUE);
            auditLogRecorder.record(SYSTEM_ACTOR_ID, AuditAction.TUITION_BILL_OVERDUE, "TUITION_BILL", tuitionBill.getId(),
                    Map.of("dueDate", tuitionBill.getDueDate()), null);
        }
        if (!targets.isEmpty()) {
            log.info("기한이 지난 등록금 고지 {}건 OVERDUE 처리", targets.size());
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void transitionOverdueInstallmentItems() {
        List<InstallmentPlanItem> targets = installmentPlanItemRepository.findByStatusAndDueDateBefore(
                InstallmentItemStatus.SCHEDULED, LocalDate.now());
        for (InstallmentPlanItem item : targets) {
            item.markOverdue();
            auditLogRecorder.record(SYSTEM_ACTOR_ID, AuditAction.INSTALLMENT_ITEM_OVERDUE, "INSTALLMENT_PLAN_ITEM", item.getId(),
                    Map.of("installmentPlanId", item.getInstallmentPlanId(), "roundNo", item.getRoundNo(), "dueDate", item.getDueDate()), null);
        }
        if (!targets.isEmpty()) {
            log.info("기한이 지난 분할납부 회차 {}건 OVERDUE 처리", targets.size());
        }
    }
}

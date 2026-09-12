package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.scheduling.CronScheduling;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 기한(due_date)이 지났는데 미납인 등록금 고지·분할납부 회차를 OVERDUE로 전환한다.
 *
 * <p>spring.threads.virtual.enabled=true 환경에서 Spring {@code @Scheduled}가 배포 pod에서 실행되지
 * 않는 현상이 확인돼(academic의 OutboxWorker 참고), 별도 ScheduledExecutorService로 직접 폴링한다.
 * 각 전환 메서드의 @Transactional은 AOP 프록시를 거쳐야 적용되므로, this로 직접 호출하지 않고
 * 지연 주입한 자기 자신(self)의 프록시를 통해 호출한다(self-invocation 우회).
 */
@Slf4j
@Component
public class OverdueTransitionScheduler {

    private static final String CRON = "0 */10 * * * *";
    private static final List<TuitionBillStatus> OVERDUE_ELIGIBLE_STATUSES =
            List.of(TuitionBillStatus.UNPAID, TuitionBillStatus.PARTIAL);
    // 스케줄러는 로그인 사용자가 없는 시스템 동작이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다.
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final TuitionBillRepository tuitionBillRepository;
    private final InstallmentPlanItemRepository installmentPlanItemRepository;
    private final AuditLogRecorder auditLogRecorder;
    private final OverdueTransitionScheduler self;

    private ScheduledExecutorService scheduler;

    public OverdueTransitionScheduler(TuitionBillRepository tuitionBillRepository,
                                       InstallmentPlanItemRepository installmentPlanItemRepository,
                                       AuditLogRecorder auditLogRecorder,
                                       @Lazy OverdueTransitionScheduler self) {
        this.tuitionBillRepository = tuitionBillRepository;
        this.installmentPlanItemRepository = installmentPlanItemRepository;
        this.auditLogRecorder = auditLogRecorder;
        this.self = self;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "overdue-transition-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        CronScheduling.scheduleCron(scheduler, CRON, ZoneId.systemDefault(), this::runTransitionOverdueTuitionBills);
        CronScheduling.scheduleCron(scheduler, CRON, ZoneId.systemDefault(), this::runTransitionOverdueInstallmentItems);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runTransitionOverdueTuitionBills() {
        try {
            self.transitionOverdueTuitionBills();
        } catch (Exception exception) {
            log.error("기한이 지난 등록금 고지 OVERDUE 처리 중 예상치 못한 예외 발생", exception);
        }
    }

    private void runTransitionOverdueInstallmentItems() {
        try {
            self.transitionOverdueInstallmentItems();
        } catch (Exception exception) {
            log.error("기한이 지난 분할납부 회차 OVERDUE 처리 중 예상치 못한 예외 발생", exception);
        }
    }

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

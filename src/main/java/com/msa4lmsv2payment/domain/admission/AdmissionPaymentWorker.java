package com.msa4lmsv2payment.domain.admission;
import com.msa4lmsv2payment.domain.tuitionbill.entity.*;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.math.BigDecimal;
@Component @RequiredArgsConstructor @Slf4j
public class AdmissionPaymentWorker {
    private final TuitionBillRepository bills;private final VirtualAccountRepository accounts;
    private final AdmissionAcademicClient academic;private final AdmissionPaymentRecorder recorder;private final TossPaymentsClient toss;
    private final com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountDepositRecorderService deposits;
    @Value("${admission.sync-delay-ms:10000}")
    private long syncDelayMs = 10000;
    private ScheduledExecutorService scheduler;

    // 기존 Payment 스케줄러와 동일하게 가상 스레드 설정에 영향받지 않는 전용 폴링 스레드를 사용한다.
    @PostConstruct
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "admission-payment-sync");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                synchronize();
            } catch (Exception failure) {
                log.warn("입학 납부 연결 조회 실패: 다음 주기에 재시도합니다.");
            }
        }, syncDelayMs, syncDelayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // 고지 자체의 저장된 상태가 전달 대기열이다. 재시작/중복 실행에도 같은 고지로 재확인한다.
    public void synchronize() {
        for(var b:bills.findAdmissionSyncBatch(LocalDateTime.now(),PageRequest.of(0,20))) {
            try { synchronizeBill(b.getId()); }
            catch(Exception failure) { recorder.deferred(b.getId(),"ADMISSION_SYNC_RETRY"); log.warn("입학 납부 연결 재시도 예정 (billId={})",b.getId()); }
        }
    }
    public void synchronizeBill(Long billId) {
        var b=bills.findById(billId).orElseThrow();
        if(b.isAdmissionSyncComplete() || b.getAdmissionNextSyncAt().isAfter(LocalDateTime.now()))return;
        var c=academic.get(b.getAdmissionCandidateId());
        var a=accounts.findByOrderId(b.currentAdmissionOrderId()).orElse(null);
        if("CANCELLED".equals(c.status())) {
            if(a!=null && b.getStatus()!=TuitionBillStatus.PAID) {
                var pg=toss.getPaymentByOrderId(a.getOrderId());
                if(pg.isDone()) {
                    if(reconcileDeposit(b, a, pg) && a.getStatus()==com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus.EXPIRED) recorder.cancelled(billId);
                    return;
                }
                if("WAITING_FOR_DEPOSIT".equals(pg.status())) {
                    var cancelled=toss.cancelPayment(a.getPaymentKey(),"입학 등록 취소",null,null,"ADMISSION-CANCEL-"+billId);
                    if(cancelled==null || !"CANCELED".equals(cancelled.status()))throw new IllegalStateException("가상계좌 취소 미확인");
                } else if(!java.util.Set.of("CANCELED","EXPIRED","ABORTED").contains(pg.status()))throw new IllegalStateException("입금 상태 확인 필요");
            }
            recorder.cancelled(billId);return;
        }
        if(a==null) { recorder.deferred(billId,null);return; }
        var pg=toss.getPaymentByOrderId(a.getOrderId());
        if(b.getStatus()!=TuitionBillStatus.PAID) {
            if(pg.isDone()) reconcileDeposit(b, a, pg);
            else recorder.deferred(billId,null);
            return;
        }
        if(!matchesFullPayment(b, a, pg)) {
            recorder.deferred(billId,"PAYMENT_REVERIFICATION_REQUIRED");return;
        }
        c=academic.post(b.getAdmissionCandidateId(),"paid",billId);
        if("COMPLETED".equals(c.status()) && c.studentId()!=null)recorder.synced(billId,c.studentId());
        else recorder.deferred(billId,null);
    }

    private boolean matchesFullPayment(TuitionBill bill,
            com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount account,
            com.msa4lmsv2payment.global.client.TossPaymentResponse pg) {
        return pg != null && pg.isDone() && account.getOrderId().equals(pg.orderId())
                && account.getPaymentKey() != null && account.getPaymentKey().equals(pg.paymentKey())
                && pg.totalAmount() != null && pg.totalAmount().equals(pg.balanceAmount())
                && bill.getBillingAmount().compareTo(BigDecimal.valueOf(pg.totalAmount())) == 0;
    }

    private boolean reconcileDeposit(TuitionBill bill,
            com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount account,
            com.msa4lmsv2payment.global.client.TossPaymentResponse pg) {
        if(!matchesFullPayment(bill, account, pg) || pg.lastTransactionKey()==null || pg.lastTransactionKey().isBlank()) {
            recorder.deferred(bill.getId(), "DEPOSIT_RECONCILIATION_REVIEW");
            return false;
        }
        // 웹훅 유실을 PG의 실제 거래키로 복구한다. 이후 웹훅도 같은 거래키로 중복 제거된다.
        deposits.recordDeposit(account.getId(), BigDecimal.valueOf(pg.totalAmount()), pg.lastTransactionKey(),
                "admission-reconcile-" + pg.lastTransactionKey(), LocalDateTime.now());
        recorder.deferred(bill.getId(), null);
        return true;
    }
}

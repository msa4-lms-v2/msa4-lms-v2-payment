package com.msa4lmsv2payment.domain.admission;
import com.msa4lmsv2payment.domain.tuitionbill.entity.*;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.service.*;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.*;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountRecorderService;
import com.msa4lmsv2payment.global.client.TossVirtualAccountIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.*;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class AdmissionPaymentRecorder {
    private final TuitionBillRepository bills; private final TuitionBillRecorderService recorder;
    private final VirtualAccountRepository accounts; private final VirtualAccountRecorderService accountRecorder;
    private final PaymentRepository payments;
    private final com.msa4lmsv2payment.domain.refund.service.RefundRecorderService refundRecorder;
    private final com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountDepositRepository deposits;
    public TuitionBill reserve(Long candidateId,String name,Long adminId,AdmissionBillRequest request) {
        var found=bills.findByAdmissionCandidateId(candidateId);
        if(found.isPresent()) {
            var b=found.get();
            if(!b.getSemesterId().equals(request.semesterId()) || b.getBillingAmount().compareTo(request.billingAmount())!=0 || !b.getDueDate().equals(request.dueDate()) || !b.getAdmissionBankCode().equals(request.bankCode()))
                throw new AdmissionPaymentConflictException("이미 발급 요청된 고지의 학기·금액·기한·은행과 일치해야 합니다.");
            return b;
        }
        var b=new TuitionBill(null,request.semesterId(),request.billingAmount(),request.dueDate(),TuitionBillStatus.UNPAID,adminId);
        b.admission(candidateId,name,request.bankCode());return recorder.saveWithAudit(adminId,b,List.of(new TuitionBillItemSpec("입학 등록금",request.billingAmount())));
    }
    public VirtualAccount issued(Long billId,String orderId,TossVirtualAccountIssueResponse r,Long adminId) {
        var b=bills.findByIdForUpdate(billId).orElseThrow();
        if (!b.currentAdmissionOrderId().equals(orderId)) throw new AdmissionPaymentConflictException("더 최신 발급 요청이 존재합니다.");
        var existing=accounts.findByOrderId(orderId);if(existing.isPresent())return existing.get();
        if(r==null || r.virtualAccount()==null || r.paymentKey()==null || r.secret()==null)throw new IllegalStateException("가상계좌 발급 응답 확인 필요");
        LocalDateTime expiry=OffsetDateTime.parse(r.virtualAccount().dueDate()).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        var a=new VirtualAccount(billId,orderId,r.secret(),r.virtualAccount().accountNumber(),r.virtualAccount().bankCode(),expiry,
                expiry.isAfter(LocalDateTime.now()) ? VirtualAccountStatus.ISSUED : VirtualAccountStatus.EXPIRED);
        b.resumeAdmissionSync();
        a.assignPaymentKey(r.paymentKey());return accountRecorder.saveWithAudit(adminId,a);
    }
    public TuitionBill prepareReissue(Long billId, AdmissionReissueRequest request) {
        var bill = bills.findByIdForUpdate(billId).orElseThrow();
        if (bill.getStatus()==TuitionBillStatus.PAID || bill.getStatus()==TuitionBillStatus.PARTIAL || bill.getStudentId()!=null) {
            throw new AdmissionPaymentConflictException("납부 처리 중이거나 완료된 고지는 재발급할 수 없습니다.");
        }
        var previous = accounts.findById(request.previousVirtualAccountId()).orElseThrow();
        if (!billId.equals(previous.getTuitionBillId())) throw new AdmissionPaymentConflictException("고지와 이전 계좌가 일치하지 않습니다.");
        String nextOrder = "ADMISSION-" + billId + "-R" + previous.getId();
        if (nextOrder.equals(bill.currentAdmissionOrderId())) {
            if (!bill.getDueDate().equals(request.dueDate())) throw new AdmissionPaymentConflictException("진행 중인 재발급 기한과 일치해야 합니다.");
            return bill;
        }
        if (!previous.getOrderId().equals(bill.currentAdmissionOrderId()) || bill.getStatus()==TuitionBillStatus.PAID
                || request.dueDate().isBefore(LocalDate.now())
                || bill.getStatus()==TuitionBillStatus.PARTIAL || bill.getStudentId()!=null
                || previous.getExpiresAt().isAfter(LocalDateTime.now()) || previous.getStatus()==VirtualAccountStatus.DEPOSITED
                || !deposits.findByVirtualAccountId(previous.getId()).isEmpty()) {
            throw new AdmissionPaymentConflictException("미입금 상태의 현재 만료 계좌만 재발급할 수 있습니다. 입금 내역이 있으면 환불 확인이 필요합니다.");
        }
        previous.expire();
        bill.prepareAdmissionReissue(nextOrder, request.dueDate());
        return bill;
    }
    public void synced(Long billId,Long studentId) {
        var b=bills.findByIdForUpdate(billId).orElseThrow();
        if(b.getStatus()!=TuitionBillStatus.PAID)throw new IllegalStateException("완납 고지가 아닙니다.");
        b.linkAdmissionStudent(studentId);payments.findByTuitionBillId(billId).forEach(p->p.linkAdmissionStudent(studentId));
    }
    public void deferred(Long billId,String error) { bills.findByIdForUpdate(billId).orElseThrow().deferAdmissionSync(error); }
    public void cancelled(Long billId) {
        var b=bills.findByIdForUpdate(billId).orElseThrow();
        if(b.isAdmissionSyncComplete())return;
        var va=accounts.findByOrderId(b.currentAdmissionOrderId()).orElse(null);
        if(va!=null) {
            if(b.getStatus()==TuitionBillStatus.PAID) {
                var refund=new com.msa4lmsv2payment.domain.refund.entity.Refund(billId,
                    com.msa4lmsv2payment.domain.refund.entity.RefundType.EXCESS_DEPOSIT,b.getBillingAmount(),java.math.BigDecimal.ONE,
                    com.msa4lmsv2payment.domain.refund.entity.RefundStatus.REQUESTED);
                refund.linkVirtualAccount(va.getId());refundRecorder.saveExcessDepositRefund(0L,refund);
            } else va.expire();
        }
        b.finishAdmissionSync();
    }
}

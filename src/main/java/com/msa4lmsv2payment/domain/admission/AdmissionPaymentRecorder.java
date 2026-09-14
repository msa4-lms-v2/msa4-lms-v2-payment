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
        var existing=accounts.findByOrderId(orderId);if(existing.isPresent())return existing.get();
        if(r==null || r.virtualAccount()==null || r.paymentKey()==null || r.secret()==null)throw new IllegalStateException("가상계좌 발급 응답 확인 필요");
        LocalDateTime expiry=OffsetDateTime.parse(r.virtualAccount().dueDate()).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        var a=new VirtualAccount(billId,orderId,r.secret(),r.virtualAccount().accountNumber(),r.virtualAccount().bankCode(),expiry,VirtualAccountStatus.ISSUED);
        b.resumeAdmissionSync();
        a.assignPaymentKey(r.paymentKey());return accountRecorder.saveWithAudit(adminId,a);
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
        var va=accounts.findByTuitionBillId(billId).orElse(null);
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

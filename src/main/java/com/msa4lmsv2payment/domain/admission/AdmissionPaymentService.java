package com.msa4lmsv2payment.domain.admission;
import com.msa4lmsv2payment.domain.tuitionbill.entity.*;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.response.VirtualAccountResponseDTO;
import com.msa4lmsv2payment.global.client.*;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service @RequiredArgsConstructor
public class AdmissionPaymentService {
    private final AdmissionAcademicClient academic;private final AcademicClient catalog;
    private final TuitionBillRepository bills;private final VirtualAccountRepository accounts;
    private final AdmissionPaymentRecorder recorder;private final TossPaymentsClient toss;
    public record Detail(TuitionBillResponseDTO bill,VirtualAccountResponseDTO virtualAccount,String syncError,boolean syncComplete,String bankCode) {}
    public Detail get(Long candidateId) {
        var b=bills.findByAdmissionCandidateId(candidateId).orElse(null);if(b==null)return null;
        return new Detail(TuitionBillResponseDTO.from(b),accounts.findByTuitionBillId(b.getId()).map(VirtualAccountResponseDTO::from).orElse(null),b.getAdmissionSyncError(),b.isAdmissionSyncComplete(),b.getAdmissionBankCode());
    }
    public Detail issue(Long candidateId,AdmissionBillRequest request,CurrentUser admin) {
        var c=academic.get(candidateId);
        if(!"PENDING".equals(c.status()) || c.tuitionPaid() || c.studentId()!=null || c.advisorProfessorId()==null)
            throw new AdmissionPaymentConflictException("완납 전 등록 대기 대상과 지도교수를 확인하세요.");
        var semester=catalog.findSemester(request.semesterId());
        if(semester.startDate()==null || semester.startDate().getYear()!=c.admissionYear()) throw new AdmissionPaymentConflictException("입학 연도와 고지 학기가 일치해야 합니다.");
        var b=recorder.reserve(candidateId,c.name(),admin.id(),request);
        var bound=academic.post(candidateId,"bill",b.getId());
        if(!bound.name().equals(b.getAdmissionCustomerName()) || bound.admissionYear()!=semester.startDate().getYear()
                || !"PENDING".equals(bound.status()) || !b.getId().equals(bound.tuitionBillId()))
            throw new AdmissionPaymentConflictException("고지 생성 중 입학 정보가 변경됐습니다. 관리자 확인이 필요합니다.");
        String orderId="ADMISSION-"+b.getId();
        if(accounts.findByOrderId(orderId).isEmpty()) {
            var response=toss.findVirtualAccountByOrderId(orderId);
            if(response==null) response=toss.issueVirtualAccount(orderId,"입학 등록금",b.getBillingAmount(),b.getAdmissionCustomerName(),b.getAdmissionBankCode(),b.getDueDate().atTime(23,59,59).atOffset(java.time.ZoneOffset.ofHours(9)).toString());
            else {
                var pg=toss.getPaymentByOrderId(orderId);
                if(pg==null || !orderId.equals(pg.orderId()) || !response.paymentKey().equals(pg.paymentKey())
                        || pg.totalAmount()==null || b.getBillingAmount().compareTo(java.math.BigDecimal.valueOf(pg.totalAmount()))!=0
                        || !("WAITING_FOR_DEPOSIT".equals(pg.status()) || pg.isDone()))
                    throw new AdmissionPaymentConflictException("기존 발급 계좌의 금액·상태를 확인해야 합니다. 새 계좌를 중복 발급하지 않습니다.");
            }
            recorder.issued(b.getId(),orderId,response,admin.id());
        }
        return get(candidateId);
    }
}

package com.msa4lmsv2payment.domain.tuitionbill.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tuition_bills")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TuitionBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;
    @jakarta.persistence.Column(unique = true)
    private Long admissionCandidateId;
    private String admissionCustomerName;
    private String admissionBankCode;
    private String admissionOrderId;
    private LocalDateTime admissionNextSyncAt;
    private boolean admissionSyncComplete;
    private String admissionSyncError;
    public void admission(Long candidateId,String name,String bank) {
        admissionCandidateId=candidateId; admissionCustomerName=name; admissionBankCode=bank;
        admissionNextSyncAt=LocalDateTime.now();
    }
    public void deferAdmissionSync(String error) {
        var next=LocalDateTime.now().plusSeconds(30);
        if(admissionNextSyncAt==null || admissionNextSyncAt.isBefore(next)) admissionNextSyncAt=next;
        admissionSyncError=error;
    }
    public void waitForDepositVerification() { admissionNextSyncAt=LocalDateTime.now().plusMinutes(3); }
    public void resumeAdmissionSync() { admissionSyncComplete=false; admissionNextSyncAt=LocalDateTime.now(); }
    public String currentAdmissionOrderId() {
        if (admissionCandidateId == null) throw new IllegalStateException("입학 고지가 아닙니다.");
        return admissionOrderId == null ? "ADMISSION-" + id : admissionOrderId;
    }
    public void prepareAdmissionReissue(String orderId, LocalDate nextDueDate) {
        if (studentId != null || status == TuitionBillStatus.PAID || status == TuitionBillStatus.PARTIAL) {
            throw new IllegalStateException("납부 중이거나 완료된 고지는 재발급할 수 없습니다.");
        }
        admissionOrderId = orderId;
        dueDate = nextDueDate;
        status = TuitionBillStatus.UNPAID;
        resumeAdmissionSync();
    }
    public void finishAdmissionSync() { admissionSyncComplete=true; admissionSyncError=null; }
    public void linkAdmissionStudent(Long id) {
        if(admissionCandidateId==null || id==null || (studentId!=null && !studentId.equals(id))) throw new IllegalStateException("입학 학생 연결 불일치");
        studentId=id; finishAdmissionSync();
    }

    private Long semesterId;

    private BigDecimal billingAmount;

    private Long tuitionRateId;

    public void assignTuitionRate(Long rateId) {
        this.tuitionRateId = rateId;
    }

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private TuitionBillStatus status;

    private Long createdBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public TuitionBill(Long studentId, Long semesterId, BigDecimal billingAmount, LocalDate dueDate,
                        TuitionBillStatus status, Long createdBy) {
        this.studentId = studentId;
        this.semesterId = semesterId;
        this.billingAmount = billingAmount;
        this.dueDate = dueDate;
        this.status = status;
        this.createdBy = createdBy;
    }

    public void changeStatus(TuitionBillStatus status) {
        this.status = status;
    }
}

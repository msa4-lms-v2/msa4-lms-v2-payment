package com.msa4lmsv2payment.domain.tuitionbill.repository;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TuitionBillRepository extends JpaRepository<TuitionBill, Long> {
    Optional<TuitionBill> findByAdmissionCandidateId(Long admissionCandidateId);
    @Query("select t from TuitionBill t where t.admissionCandidateId is not null and t.admissionSyncComplete = false and t.admissionNextSyncAt <= :now order by t.admissionNextSyncAt, t.id")
    List<TuitionBill> findAdmissionSyncBatch(@Param("now") java.time.LocalDateTime now,org.springframework.data.domain.Pageable pageable);
    List<TuitionBill> findByStudentIdOrderByDueDateDesc(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TuitionBill t where t.id = :id")
    Optional<TuitionBill> findByIdForUpdate(@Param("id") Long id);

    List<TuitionBill> findByStatusInAndDueDateBefore(List<TuitionBillStatus> statuses, LocalDate date);

    @Query("select t from TuitionBill t where t.status in :statuses and t.dueDate < :date "
            + "and not exists (select p.id from InstallmentPlan p where p.tuitionBillId = t.id and p.status in "
            + "(com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus.ACTIVE, "
            + "com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus.COMPLETED))")
    List<TuitionBill> findOverdueWithoutApprovedPlan(@Param("statuses") List<TuitionBillStatus> statuses, @Param("date") LocalDate date);

    Optional<TuitionBill> findByStudentIdAndSemesterId(Long studentId, Long semesterId);
}

package com.msa4lmsv2payment.domain.tuitionbill.repository;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TuitionBillRepository extends JpaRepository<TuitionBill, Long> {
    List<TuitionBill> findByStudentIdOrderByDueDateDesc(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TuitionBill t where t.id = :id")
    Optional<TuitionBill> findByIdForUpdate(@Param("id") Long id);
}

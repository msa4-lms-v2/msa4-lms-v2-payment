package com.msa4lmsv2payment.domain.tuitionbill.repository;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TuitionBillRepository extends JpaRepository<TuitionBill, Long> {
    List<TuitionBill> findByStudentIdOrderByDueDateDesc(Long studentId);
}

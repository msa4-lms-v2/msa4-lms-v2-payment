package com.msa4lmsv2payment.domain.scholarship.repository;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
    List<Scholarship> findByTuitionBillId(Long tuitionBillId);
}

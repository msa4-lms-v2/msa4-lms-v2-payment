package com.msa4lmsv2payment.domain.scholarshipapplication.repository;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScholarshipApplicationRepository extends JpaRepository<ScholarshipApplication, Long> {
    List<ScholarshipApplication> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Optional<ScholarshipApplication> findByTuitionBillIdAndStatus(Long tuitionBillId, ScholarshipApplicationStatus status);
}

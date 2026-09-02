package com.msa4lmsv2payment.domain.scholarshipapplication.repository;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScholarshipApplicationPeriodRepository extends JpaRepository<ScholarshipApplicationPeriod, Long> {
    Optional<ScholarshipApplicationPeriod> findTopBySemesterIdOrderByCreatedAtDesc(Long semesterId);
}

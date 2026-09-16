package com.msa4lmsv2payment.domain.tuitionrate.repository;

import com.msa4lmsv2payment.domain.tuitionrate.entity.DepartmentTuitionRate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentTuitionRateRepository extends JpaRepository<DepartmentTuitionRate, Long> {
    Optional<DepartmentTuitionRate> findByDepartmentIdAndSemesterId(Long departmentId, Long semesterId);
}

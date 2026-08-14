package com.msa4lmsv2payment.domain.installment.repository;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, Long> {
    Optional<InstallmentPlan> findByTuitionBillId(Long tuitionBillId);
}

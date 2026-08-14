package com.msa4lmsv2payment.domain.installment.repository;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentPlanItemRepository extends JpaRepository<InstallmentPlanItem, Long> {
    List<InstallmentPlanItem> findByInstallmentPlanIdOrderByRoundNo(Long installmentPlanId);

    long countByInstallmentPlanIdAndStatus(Long installmentPlanId, InstallmentItemStatus status);
}

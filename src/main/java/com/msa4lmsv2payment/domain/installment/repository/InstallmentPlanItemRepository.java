package com.msa4lmsv2payment.domain.installment.repository;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InstallmentPlanItemRepository extends JpaRepository<InstallmentPlanItem, Long> {
    List<InstallmentPlanItem> findByInstallmentPlanIdOrderByRoundNo(Long installmentPlanId);

    long countByInstallmentPlanIdAndStatus(Long installmentPlanId, InstallmentItemStatus status);

    List<InstallmentPlanItem> findByStatusAndDueDateBefore(InstallmentItemStatus status, LocalDate date);

    @Query("select i from InstallmentPlanItem i where i.status = :status and i.dueDate < :date "
            + "and exists (select p.id from InstallmentPlan p where p.id = i.installmentPlanId "
            + "and p.status = com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus.ACTIVE)")
    List<InstallmentPlanItem> findOverdueActiveItems(@Param("status") InstallmentItemStatus status, @Param("date") LocalDate date);
}

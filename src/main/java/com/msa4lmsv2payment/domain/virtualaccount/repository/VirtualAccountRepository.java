package com.msa4lmsv2payment.domain.virtualaccount.repository;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {
    Optional<VirtualAccount> findByTuitionBillId(Long tuitionBillId);

    Optional<VirtualAccount> findByOrderId(String orderId);

    List<VirtualAccount> findByStatusInAndExpiresAtBefore(List<VirtualAccountStatus> statuses, LocalDateTime now);

    Optional<VirtualAccount> findByInstallmentPlanItemId(Long installmentPlanItemId);
}

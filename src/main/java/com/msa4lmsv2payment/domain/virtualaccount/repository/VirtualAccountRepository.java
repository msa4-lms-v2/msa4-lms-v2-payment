package com.msa4lmsv2payment.domain.virtualaccount.repository;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, Long> {
    Optional<VirtualAccount> findByTuitionBillId(Long tuitionBillId);

    Optional<VirtualAccount> findByOrderId(String orderId);
}

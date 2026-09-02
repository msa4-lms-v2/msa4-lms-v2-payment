package com.msa4lmsv2payment.domain.virtualaccount.repository;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface VirtualAccountDepositRepository extends JpaRepository<VirtualAccountDeposit, Long> {

    boolean existsByTossTransactionKey(String tossTransactionKey);

    List<VirtualAccountDeposit> findByVirtualAccountId(Long virtualAccountId);

    default BigDecimal sumAmount(Long virtualAccountId) {
        return findByVirtualAccountId(virtualAccountId).stream()
                .map(VirtualAccountDeposit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

package com.msa4lmsv2payment.domain.refund.repository;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByTuitionBillIdAndRefundType(Long tuitionBillId, RefundType refundType);
}

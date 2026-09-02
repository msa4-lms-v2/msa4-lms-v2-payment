package com.msa4lmsv2payment.domain.refund.repository;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByTuitionBillIdAndRefundType(Long tuitionBillId, RefundType refundType);

    List<Refund> findByTuitionBillIdAndStatus(Long tuitionBillId, RefundStatus status);

    default BigDecimal sumSucceededAmount(Long tuitionBillId) {
        return findByTuitionBillIdAndStatus(tuitionBillId, RefundStatus.SUCCEEDED).stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

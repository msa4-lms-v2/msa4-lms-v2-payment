package com.msa4lmsv2payment.domain.payment.repository;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgTransactionId(String pgTransactionId);

    List<Payment> findByTuitionBillIdAndStatus(Long tuitionBillId, PaymentStatus status);

    default BigDecimal sumSucceededAmount(Long tuitionBillId) {
        return findByTuitionBillIdAndStatus(tuitionBillId, PaymentStatus.SUCCEEDED).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

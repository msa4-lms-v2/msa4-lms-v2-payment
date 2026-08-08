package com.msa4lmsv2payment.domain.tuitionbill.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionPaymentStatusResponseDTO(
        Long tuitionBillId,
        BigDecimal billingAmount,
        LocalDate dueDate,
        TuitionBillStatus status
) {
    public static TuitionPaymentStatusResponseDTO from(TuitionBill tuitionBill) {
        return new TuitionPaymentStatusResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getBillingAmount(),
                tuitionBill.getDueDate(),
                tuitionBill.getStatus()
        );
    }
}

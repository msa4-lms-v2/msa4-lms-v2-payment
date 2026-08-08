package com.msa4lmsv2payment.domain.tuitionbill.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TuitionBillResponseDTO(
        Long id,
        Long studentId,
        Long semesterId,
        BigDecimal billingAmount,
        LocalDate dueDate,
        TuitionBillStatus status
) {
    public static TuitionBillResponseDTO from(TuitionBill tuitionBill) {
        return new TuitionBillResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getStudentId(),
                tuitionBill.getSemesterId(),
                tuitionBill.getBillingAmount(),
                tuitionBill.getDueDate(),
                tuitionBill.getStatus()
        );
    }
}

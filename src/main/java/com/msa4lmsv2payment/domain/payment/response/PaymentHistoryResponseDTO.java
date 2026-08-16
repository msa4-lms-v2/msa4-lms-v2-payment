package com.msa4lmsv2payment.domain.payment.response;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.entity.PaymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryResponseDTO(
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "Academic.semesters.id") Long semesterId,
        @Schema(description = "납부 구분") PaymentType paymentType,
        @Schema(description = "납부 완료 일시(완료 전이면 null)") LocalDateTime paymentDate,
        @Schema(description = "결제 금액") BigDecimal amount,
        @Schema(description = "결제 상태") PaymentStatus status
) {
}

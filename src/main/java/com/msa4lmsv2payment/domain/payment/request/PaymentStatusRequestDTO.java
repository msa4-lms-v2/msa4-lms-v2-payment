package com.msa4lmsv2payment.domain.payment.request;

import jakarta.validation.constraints.NotNull;

public record PaymentStatusRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId
) {
}

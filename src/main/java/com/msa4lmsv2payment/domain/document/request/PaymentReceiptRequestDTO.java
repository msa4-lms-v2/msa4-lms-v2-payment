package com.msa4lmsv2payment.domain.document.request;

import jakarta.validation.constraints.NotNull;

public record PaymentReceiptRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId
) {
}

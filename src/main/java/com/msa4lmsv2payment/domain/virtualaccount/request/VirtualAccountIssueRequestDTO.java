package com.msa4lmsv2payment.domain.virtualaccount.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VirtualAccountIssueRequestDTO(
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @NotBlank(message = "은행 코드는 필수입니다.") String bankCode,
        @NotBlank(message = "입금자명은 필수입니다.") String customerName
) {
}

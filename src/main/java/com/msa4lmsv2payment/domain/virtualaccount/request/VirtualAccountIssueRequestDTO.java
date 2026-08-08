package com.msa4lmsv2payment.domain.virtualaccount.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VirtualAccountIssueRequestDTO(
        @Schema(description = "등록금 고지 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "등록금 고지 ID는 필수입니다.") Long tuitionBillId,
        @Schema(description = "토스페이먼츠 은행 코드(2자리)", example = "020", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "은행 코드는 필수입니다.") String bankCode,
        @Schema(description = "입금자명", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "입금자명은 필수입니다.") String customerName
) {
}

package com.msa4lmsv2payment.domain.document.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRevokeRequestDTO(
        @Schema(description = "폐기 사유", example = "오발급 정정", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "폐기 사유는 필수입니다.") @Size(max = 255, message = "폐기 사유는 255자를 넘을 수 없습니다.") String reason
) {
}

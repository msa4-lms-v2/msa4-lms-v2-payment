package com.msa4lmsv2payment.domain.refund.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefundExecuteRequestDTO(
        @Schema(description = "토스페이먼츠 취소 사유", example = "자퇴 환불", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "취소 사유는 필수입니다.") String cancelReason,
        @Schema(description = "환불 수취은행 코드. WITHDRAWAL/EXCESS_DEPOSIT 환불에만 필수, PG_CANCEL은 비운다.",
                example = "020", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refundBankCode,
        @Schema(description = "환불 수취계좌번호(하이픈 없이). WITHDRAWAL/EXCESS_DEPOSIT 환불에만 필수, PG_CANCEL은 비운다.",
                example = "110123456789", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refundAccountNumber,
        @Schema(description = "환불 수취계좌 예금주명. WITHDRAWAL/EXCESS_DEPOSIT 환불에만 필수, PG_CANCEL은 비운다.",
                example = "홍길동", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refundHolderName
) {
}

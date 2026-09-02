package com.msa4lmsv2payment.domain.virtualaccount.response;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record VirtualAccountResponseDTO(
        @Schema(description = "가상계좌 ID") Long id,
        @Schema(description = "등록금 고지 ID") Long tuitionBillId,
        @Schema(description = "발급된 계좌번호") String accountNumber,
        @Schema(description = "은행 코드") String bankCode,
        @Schema(description = "입금 기한") LocalDateTime expiresAt,
        @Schema(description = "계좌 상태") VirtualAccountStatus status
) {
    public static VirtualAccountResponseDTO from(VirtualAccount virtualAccount) {
        return new VirtualAccountResponseDTO(
                virtualAccount.getId(),
                virtualAccount.getTuitionBillId(),
                virtualAccount.getAccountNumber(),
                virtualAccount.getBankCode(),
                virtualAccount.getExpiresAt(),
                virtualAccount.getStatus()
        );
    }
}

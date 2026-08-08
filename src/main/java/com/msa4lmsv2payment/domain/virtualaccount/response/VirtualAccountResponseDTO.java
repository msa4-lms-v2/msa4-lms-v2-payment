package com.msa4lmsv2payment.domain.virtualaccount.response;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;

import java.time.LocalDateTime;

public record VirtualAccountResponseDTO(
        Long id,
        Long tuitionBillId,
        String accountNumber,
        String bankCode,
        LocalDateTime expiresAt,
        VirtualAccountStatus status
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

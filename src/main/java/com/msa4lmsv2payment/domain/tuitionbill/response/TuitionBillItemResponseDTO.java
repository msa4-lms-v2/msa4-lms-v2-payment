package com.msa4lmsv2payment.domain.tuitionbill.response;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record TuitionBillItemResponseDTO(
        @Schema(description = "항목 ID") Long id,
        @Schema(description = "항목명", example = "수업료") String itemName,
        @Schema(description = "금액") BigDecimal amount,
        @Schema(description = "납입 여부") boolean paid
) {
    public static TuitionBillItemResponseDTO from(TuitionBillItem item) {
        return new TuitionBillItemResponseDTO(item.getId(), item.getItemName(), item.getAmount(), item.isPaid());
    }
}

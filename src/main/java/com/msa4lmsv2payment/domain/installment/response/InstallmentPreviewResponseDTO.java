package com.msa4lmsv2payment.domain.installment.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstallmentPreviewResponseDTO(boolean requiresReview, List<Item> items) {
    public record Item(int roundNo, BigDecimal amount, LocalDate dueDate) { }
}

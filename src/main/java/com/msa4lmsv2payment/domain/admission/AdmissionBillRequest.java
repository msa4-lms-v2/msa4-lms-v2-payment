package com.msa4lmsv2payment.domain.admission;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record AdmissionBillRequest(@NotNull @Positive Long semesterId,@NotNull @DecimalMin("1") @Digits(integer=10,fraction=0) BigDecimal billingAmount,@NotNull @FutureOrPresent LocalDate dueDate,@NotBlank @Size(max=20) String bankCode) {}

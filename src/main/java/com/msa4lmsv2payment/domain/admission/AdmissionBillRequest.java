package com.msa4lmsv2payment.domain.admission;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
// billingAmount는 기존 클라이언트 호환용이며 고지 금액은 서버의 학과·학기 기준으로 확정한다.
public record AdmissionBillRequest(@NotNull @Positive Long semesterId,@DecimalMin("1") @Digits(integer=10,fraction=0) BigDecimal billingAmount,@NotNull @FutureOrPresent LocalDate dueDate,@NotBlank @Size(max=20) String bankCode) {}

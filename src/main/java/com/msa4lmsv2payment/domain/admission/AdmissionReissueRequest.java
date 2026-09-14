package com.msa4lmsv2payment.domain.admission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AdmissionReissueRequest(
        @NotNull @Positive Long previousVirtualAccountId,
        @NotNull LocalDate dueDate
) {}

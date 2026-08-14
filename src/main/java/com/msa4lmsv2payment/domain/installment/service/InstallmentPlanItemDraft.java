package com.msa4lmsv2payment.domain.installment.service;

import java.math.BigDecimal;
import java.time.LocalDate;

// 아직 installment_plan_id가 없는(plan 저장 전) 회차 초안. InstallmentPlanRecorder가 plan 저장 후 실제 엔티티로 변환한다.
record InstallmentPlanItemDraft(Integer roundNo, BigDecimal amount, LocalDate dueDate) {
}

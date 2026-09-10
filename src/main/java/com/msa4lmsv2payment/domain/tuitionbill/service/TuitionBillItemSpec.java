package com.msa4lmsv2payment.domain.tuitionbill.service;

import java.math.BigDecimal;

public record TuitionBillItemSpec(String itemName, BigDecimal amount) {
}

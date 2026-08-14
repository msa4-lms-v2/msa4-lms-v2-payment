package com.msa4lmsv2payment.domain.payment.service;

import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.error.PaymentNotFoundException;
import com.msa4lmsv2payment.global.error.TuitionOverpaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 4.4: 결제 확정 직전에 현재 순납부액을 다시 계산해 초과 납부를 거부한다.
 * 별도 Bean으로 분리한 이유: PaymentService에서 self-invocation으로 호출하면
 * @Transactional이 프록시를 거치지 않아 무시되므로, 락이 걸린 트랜잭션 경계를 보장하려면 별도 Bean이 필요하다.
 */
@Component
@RequiredArgsConstructor
public class TuitionOverpaymentGuard {

    private final TuitionBillRepository tuitionBillRepository;
    private final PaymentRepository paymentRepository;
    private final ScholarshipService scholarshipService;

    @Transactional
    public void guard(Long tuitionBillId, BigDecimal incomingAmount) {
        TuitionBill tuitionBill = tuitionBillRepository.findByIdForUpdate(tuitionBillId)
                .orElseThrow(() -> new PaymentNotFoundException("등록금 고지를 찾을 수 없습니다: " + tuitionBillId));
        BigDecimal scholarshipTotal = scholarshipService.sumScholarshipAmount(tuitionBillId);
        BigDecimal netDue = tuitionBill.getBillingAmount().subtract(scholarshipTotal).max(BigDecimal.ZERO);
        BigDecimal alreadySucceeded = paymentRepository.sumSucceededAmount(tuitionBillId);

        if (alreadySucceeded.add(incomingAmount).compareTo(netDue) > 0) {
            throw new TuitionOverpaymentException("이미 성공한 결제와 합산한 금액이 순납부액을 초과합니다.");
        }
    }
}

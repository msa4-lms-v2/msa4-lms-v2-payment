package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.service.PaymentResultRecorderService;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.service.RefundRecorderService;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountDeposit;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountDepositRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.VirtualAccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 가상계좌 입금 저장, 계좌 상태 갱신, 완납 시 결제·초과입금 환불 기록을 하나의 트랜잭션으로 묶는다.
 * Webhook은 로그인 사용자가 없는 시스템 요청이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class VirtualAccountDepositRecorderService {

    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountDepositRepository virtualAccountDepositRepository;
    private final TuitionBillService tuitionBillService;
    private final ScholarshipService scholarshipService;
    private final PaymentResultRecorderService paymentResultRecorder;
    private final RefundRecorderService refundRecorder;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public void recordDeposit(Long virtualAccountId, BigDecimal amount, String transactionKey) {
        VirtualAccount virtualAccount = virtualAccountRepository.findById(virtualAccountId)
                .orElseThrow(() -> new VirtualAccountNotFoundException("가상계좌를 찾을 수 없습니다: " + virtualAccountId));

        VirtualAccountDeposit deposit = virtualAccountDepositRepository.save(
                new VirtualAccountDeposit(virtualAccountId, amount, transactionKey, LocalDateTime.now()));
        auditLogRecorder.record(SYSTEM_ACTOR_ID, AuditAction.VIRTUAL_ACCOUNT_DEPOSIT_RECEIVED, "VIRTUAL_ACCOUNT", virtualAccountId,
                Map.of("depositId", deposit.getId(), "amount", amount), null);

        TuitionBill tuitionBill = tuitionBillService.getTuitionBillOrThrow(virtualAccount.getTuitionBillId());
        BigDecimal netDue = tuitionBill.getBillingAmount()
                .subtract(scholarshipService.sumScholarshipAmount(tuitionBill.getId()))
                .max(BigDecimal.ZERO);
        BigDecimal totalDeposited = virtualAccountDepositRepository.sumAmount(virtualAccountId);

        virtualAccount.applyDeposit(totalDeposited, netDue);
        virtualAccountRepository.save(virtualAccount);

        if (virtualAccount.getStatus() != VirtualAccountStatus.DEPOSITED) {
            return; // PARTIALLY_DEPOSITED - 나머지 입금을 기다린다.
        }

        Payment payment = new Payment(tuitionBill.getId(), tuitionBill.getStudentId(), netDue, PaymentMethod.VIRTUAL_ACCOUNT, PaymentStatus.REQUESTED);
        payment.succeed(transactionKey);
        paymentResultRecorder.saveWithAudit(SYSTEM_ACTOR_ID, payment, null);
        tuitionBillService.changeStatus(tuitionBill.getId(), TuitionBillStatus.PAID);

        BigDecimal excess = totalDeposited.subtract(netDue);
        if (excess.compareTo(BigDecimal.ZERO) > 0) {
            // 초과입금 환불에는 자퇴 환불 같은 비율 개념이 없어 refund_rate는 "전액 환불"을 뜻하는 1로 둔다.
            Refund refund = new Refund(tuitionBill.getId(), RefundType.EXCESS_DEPOSIT, excess, BigDecimal.ONE, RefundStatus.REQUESTED);
            refund.linkVirtualAccount(virtualAccountId);
            refundRecorder.saveExcessDepositRefund(SYSTEM_ACTOR_ID, refund);
        }
    }
}

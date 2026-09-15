package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.repository.InstallmentEligibilityRepository;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import java.util.List;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.global.error.InstallmentApplicationNotAllowedException;
import com.msa4lmsv2payment.global.error.InvalidInstallmentRoundsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstallmentApplicationPolicy {
    private final PaymentRepository paymentRepository;
    private final InstallmentEligibilityRepository eligibilityRepository;
    private final VirtualAccountRepository virtualAccountRepository;

    public void validate(TuitionBill bill, BigDecimal amount, Integer rounds) {
        if (rounds == null || rounds < 2 || rounds > 4) {
            throw new InvalidInstallmentRoundsException("분할 회차는 2~4회만 선택할 수 있습니다.");
        }
        if (bill.getStudentId() == null || bill.getAdmissionCandidateId() != null || bill.getStatus() != TuitionBillStatus.UNPAID
                || bill.getDueDate() == null || bill.getDueDate().isBefore(LocalDate.now())) {
            throw new InstallmentApplicationNotAllowedException("납부기한 내 미납 고지만 분할납부를 신청할 수 있습니다.");
        }
        if (amount.compareTo(BigDecimal.valueOf(rounds)) < 0) {
            throw new InstallmentApplicationNotAllowedException("회차별 납부금액이 1원 이상이어야 합니다.");
        }
        if (paymentRepository.findByTuitionBillId(bill.getId()).stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.REQUESTED || p.getStatus() == PaymentStatus.SUCCEEDED)) {
            throw new InstallmentApplicationNotAllowedException("결제 진행 중이거나 납부 이력이 있는 고지는 분할납부로 변경할 수 없습니다.");
        }
        if (virtualAccountRepository.existsByTuitionBillIdAndStatusIn(bill.getId(),
                List.of(VirtualAccountStatus.ISSUED, VirtualAccountStatus.PARTIALLY_DEPOSITED, VirtualAccountStatus.DEPOSITED))) {
            throw new InstallmentApplicationNotAllowedException("가상계좌가 발급되었거나 입금된 고지는 관리자 확인 후 납부방식을 변경해 주세요.");
        }
    }

    public boolean requiresReview(TuitionBill bill) {
        return eligibilityRepository.hasDelinquency(bill.getStudentId(), LocalDate.now());
    }
}

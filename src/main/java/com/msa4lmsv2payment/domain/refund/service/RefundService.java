package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.global.error.RefundNotFoundException;
import com.msa4lmsv2payment.global.error.RefundNotRetryableException;
import com.msa4lmsv2payment.global.error.RefundRetryLimitExceededException;
import com.msa4lmsv2payment.global.error.TuitionBillAccessDeniedException;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.RefundRetryRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.AcademicWithdrawalResponse;
import com.msa4lmsv2payment.global.error.WithdrawalNotApprovedException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final TuitionBillService tuitionBillService;
    private final VirtualAccountService virtualAccountService;
    private final AcademicClient academicClient;
    private final WithdrawalRefundRateCalculatorService withdrawalRefundRateCalculator;
    private final RefundRecorderService refundRecorder;

    // 자퇴 예상 환불금 조회 (조회만, 저장 없음)
    // resolveWithdrawalRefundRate가 Academic을 호출해 트랜잭션 밖에서 실행한다.
    // applyWithdrawalRefundRate와 같은 private 헬퍼를 공유하므로 이 조회 경로도 동일하게 적용한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WithdrawalRefundEstimateResponseDTO estimateWithdrawalRefund(
            CurrentUser currentUser, Long tuitionBillId, Long withdrawalId
    ) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        BigDecimal rate = resolveWithdrawalRefundRate(tuitionBill, withdrawalId);
        BigDecimal refundableBase = refundableBase(tuitionBill.getId());
        BigDecimal estimatedAmount = refundableBase.multiply(rate);

        return new WithdrawalRefundEstimateResponseDTO(tuitionBill.getId(), refundableBase, rate, estimatedAmount);
    }

    // 자퇴 처리일 기준 환불률 적용. 동일 고지에 재요청 시 새로 만들지 않고 기존 REQUESTED 건의 비율만 갱신해 중복 실행을 막는다.
    // Academic 호출 동안 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행한다.
    // findByTuitionBillIdAndRefundType()가 반환한 엔티티는 그 조회 자체의 트랜잭션이 끝나며 detach되므로,
    // 변경 후 반드시 save()를 다시 호출해야 반영된다(더티체킹에 기대지 않는다).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponseDTO applyWithdrawalRefundRate(CurrentUser currentUser, WithdrawalRefundRateRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        BigDecimal rate = resolveWithdrawalRefundRate(tuitionBill, request.withdrawalId());
        BigDecimal refundableBase = refundableBase(tuitionBill.getId());
        BigDecimal amount = refundableBase.multiply(rate);

        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .orElseGet(() -> new Refund(tuitionBill.getId(), RefundType.WITHDRAWAL, amount, rate, RefundStatus.REQUESTED));
        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            throw new RefundNotRetryableException("완료된 환불 금액과 환불률은 변경할 수 없습니다.");
        }
        refund.updateRate(request.withdrawalId(), amount, rate);
        refund = refundRecorder.saveRateApplied(currentUser.id(), refund, tuitionBill.getId(), amount, rate);

        return RefundResponseDTO.from(refund);
    }

    // 성공 결제 합계에서 성공 환불 합계를 뺀 값만 환불 대상이다. 장학금은 결제 자체가
    // 아니므로 자동 제외되고, 이미 환불된 금액을 다시 환불 기준액에 포함하지 않는다.
    private BigDecimal refundableBase(Long tuitionBillId) {
        BigDecimal succeededPayments = paymentRepository.sumSucceededAmount(tuitionBillId);
        BigDecimal succeededRefunds = refundRepository.sumSucceededAmount(tuitionBillId);
        return succeededPayments.subtract(succeededRefunds);
    }

    // 가상계좌 환불 요청 - 가상계좌 발급에서 만든 계좌를 자퇴 환불률 적용에서 만든 환불 요청에 연결한다.
    // 실제 입금 확인·토스 환불 접수 호출은 입금 검증 인프라(virtual_account_deposits)가 생기는 week-4에서 이어간다 -
    // 지금은 "이 계좌로 환불하겠다"는 연결까지만 한다.
    @Transactional
    public RefundResponseDTO requestVirtualAccountRefund(CurrentUser currentUser, VirtualAccountRefundRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        VirtualAccount virtualAccount = virtualAccountService.getByTuitionBillIdOrThrow(tuitionBill.getId());
        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .orElseThrow(() -> new RefundNotFoundException(
                        "먼저 자퇴 처리일 기준 환불률을 적용해야 합니다(PATCH /api/payment/refunds/withdrawal-rate)."));

        refund.linkVirtualAccount(virtualAccount.getId());

        return RefundResponseDTO.from(refund);
    }

    // 실패한 환불 재시도 - FAILED 상태만 재시도할 수 있고, MAX_RETRY_ATTEMPTS를 넘으면 최종 실패로 본다.
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponseDTO retryFailedRefund(CurrentUser currentUser, RefundRetryRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .orElseThrow(() -> new RefundNotFoundException("환불 요청을 찾을 수 없습니다."));

        if (refund.getStatus() != RefundStatus.FAILED) {
            throw new RefundNotRetryableException("실패 상태의 환불만 재시도할 수 있습니다.");
        }
        if (refund.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            throw new RefundRetryLimitExceededException(
                    "재시도 횟수(" + MAX_RETRY_ATTEMPTS + "회)를 초과해 재시도할 수 없습니다. 최종 실패 상태입니다.");
        }

        refund.retry();
        refund = refundRecorder.saveRetried(currentUser.id(), refund);

        return RefundResponseDTO.from(refund);
    }

    private BigDecimal resolveWithdrawalRefundRate(TuitionBill tuitionBill, Long withdrawalId) {
        AcademicWithdrawalResponse withdrawal = academicClient.findWithdrawal(withdrawalId);
        if (!withdrawal.studentId().equals(tuitionBill.getStudentId())) {
            throw new TuitionBillAccessDeniedException("본인의 자퇴 신청이 아닙니다.");
        }
        if (!"APPROVED".equals(withdrawal.status()) || withdrawal.effectiveDate() == null) {
            throw new WithdrawalNotApprovedException("승인되어 효력일이 확정된 자퇴 신청만 환불을 계산할 수 있습니다.");
        }
        AcademicSemesterResponse semester = academicClient.findSemester(tuitionBill.getSemesterId());
        return withdrawalRefundRateCalculator.calculate(withdrawal.effectiveDate(), semester.startDate(), semester.endDate());
    }
}

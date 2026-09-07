package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.request.PaymentStatusRequestDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.global.error.PaymentNotFoundException;
import com.msa4lmsv2payment.global.error.RefundAmountExceedsPaymentException;
import com.msa4lmsv2payment.global.error.RefundNotFoundException;
import com.msa4lmsv2payment.global.error.RefundNotRetryableException;
import com.msa4lmsv2payment.global.error.RefundRetryLimitExceededException;
import com.msa4lmsv2payment.global.error.TossPaymentRejectedException;
import com.msa4lmsv2payment.global.error.TossServiceUnavailableException;
import com.msa4lmsv2payment.global.error.TuitionBillAccessDeniedException;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.PgCancelRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.RefundExecuteRequestDTO;
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
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossRefundReceiveAccount;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final TuitionBillService tuitionBillService;
    private final PaymentService paymentService;
    private final VirtualAccountService virtualAccountService;
    private final AcademicClient academicClient;
    private final TossPaymentsClient tossPaymentsClient;
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

    // 카드 결제 취소 요청 생성 - 같은 결제에 재요청하면 새로 만들지 않고 REQUESTED 건의 금액만 갱신한다(applyWithdrawalRefundRate와 동일 원칙).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponseDTO createPgCancelRefund(CurrentUser admin, PgCancelRefundRequestDTO request) {
        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException("결제를 찾을 수 없습니다: " + request.paymentId()));
        if (!payment.isSucceeded()) {
            throw new RefundNotRetryableException("성공한 결제만 취소할 수 있습니다.");
        }

        BigDecimal alreadyRefunded = refundRepository.sumSucceededAmountByPayment(payment.getId());
        BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundAmountExceedsPaymentException("이미 전액 취소된 결제입니다.");
        }
        BigDecimal amount = request.amount() != null ? request.amount() : remaining;
        if (amount.compareTo(remaining) > 0) {
            throw new RefundAmountExceedsPaymentException("환불 가능 금액(" + remaining + ")을 초과했습니다.");
        }
        BigDecimal rate = amount.divide(payment.getAmount(), 4, RoundingMode.HALF_UP);

        Refund refund = refundRepository.findByPaymentIdAndRefundType(payment.getId(), RefundType.PG_CANCEL)
                .orElseGet(() -> new Refund(payment.getTuitionBillId(), RefundType.PG_CANCEL, amount, rate, RefundStatus.REQUESTED));
        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            throw new RefundNotRetryableException("완료된 환불 금액은 변경할 수 없습니다.");
        }
        refund.updateRate(null, amount, rate); // PG_CANCEL은 withdrawalId가 없어 null을 넘긴다.
        refund.linkPayment(payment.getId());
        refund = refundRecorder.savePgCancelRequested(admin.id(), refund);

        return RefundResponseDTO.from(refund);
    }

    // 환불 이력 조회 - STUDENT 본인 / ADMIN 관리 범위.
    // getOwnedTuitionBillOrThrow가 STUDENT 호출 시 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<RefundResponseDTO> listRefunds(CurrentUser currentUser, Long tuitionBillId) {
        tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        return refundRepository.findByTuitionBillIdOrderByRequestedAtDesc(tuitionBillId).stream()
                .map(RefundResponseDTO::from)
                .toList();
    }

    // 환불 실행 - 실제 토스 취소 API를 호출한다. REQUESTED/RETRYING은 그대로, FAILED는 재시도 한도 안에서 재시도로 전환 후 실행한다.
    // 토스 호출 동안 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행하고, 저장은 refundRecorder(별도 트랜잭션)에 위임한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponseDTO executeRefund(CurrentUser admin, Long refundId, RefundExecuteRequestDTO request, String idempotencyKey) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("환불을 찾을 수 없습니다: " + refundId));

        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            throw new RefundNotRetryableException("이미 완료된 환불입니다.");
        }
        if (refund.getStatus() == RefundStatus.FAILED) {
            if (refund.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                throw new RefundRetryLimitExceededException(
                        "재시도 횟수(" + MAX_RETRY_ATTEMPTS + "회)를 초과해 재시도할 수 없습니다. 최종 실패 상태입니다.");
            }
            refund.retry();
            refund = refundRecorder.saveRetried(admin.id(), refund);
        }

        String paymentKey = resolvePaymentKeyForExecution(refund);
        TossRefundReceiveAccount receiveAccount = null;
        if (refund.getRefundType() != RefundType.PG_CANCEL) {
            refund.linkRefundReceiveAccount(request.refundBankCode(), request.refundAccountNumber(), request.refundHolderName());
            receiveAccount = new TossRefundReceiveAccount(request.refundBankCode(), request.refundAccountNumber(), request.refundHolderName());
        }

        try {
            tossPaymentsClient.cancelPayment(paymentKey, request.cancelReason(), refund.getAmount(), receiveAccount, idempotencyKey);
        } catch (TossPaymentRejectedException | TossServiceUnavailableException e) {
            refund.fail();
            refund = refundRecorder.saveFailed(admin.id(), refund, e.getMessage());
            return RefundResponseDTO.from(refund);
        }

        refund.succeed();
        refund = refundRecorder.saveSucceeded(admin.id(), refund);
        paymentService.recalculateTuitionStatus(admin, new PaymentStatusRequestDTO(refund.getTuitionBillId()));

        return RefundResponseDTO.from(refund);
    }

    private String resolvePaymentKeyForExecution(Refund refund) {
        if (refund.getRefundType() == RefundType.PG_CANCEL) {
            Payment payment = paymentRepository.findById(refund.getPaymentId())
                    .orElseThrow(() -> new PaymentNotFoundException("결제를 찾을 수 없습니다: " + refund.getPaymentId()));
            return payment.getPgTransactionId();
        }
        VirtualAccount virtualAccount = virtualAccountService.getByIdOrThrow(refund.getVirtualAccountId());
        return virtualAccount.getPaymentKey();
    }

    private BigDecimal resolveWithdrawalRefundRate(TuitionBill tuitionBill, Long withdrawalId) {
        AcademicWithdrawalResponse withdrawal = academicClient.findWithdrawal(withdrawalId);
        if (!withdrawal.studentId().equals(tuitionBill.getStudentId())) {
            throw new TuitionBillAccessDeniedException("본인의 자퇴 신청이 아닙니다.");
        }
        AcademicSemesterResponse semester = academicClient.findSemester(tuitionBill.getSemesterId());
        return withdrawalRefundRateCalculator.calculate(withdrawal.effectiveDate(), semester.startDate(), semester.endDate());
    }
}

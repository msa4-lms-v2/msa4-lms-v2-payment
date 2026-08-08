package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.WithdrawalRefundRateCalculator;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.error.RefundNotFoundException;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.AcademicWithdrawalHistoryResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private final RefundRepository refundRepository;
    private final TuitionBillService tuitionBillService;
    private final VirtualAccountService virtualAccountService;
    private final AcademicClient academicClient;
    private final WithdrawalRefundRateCalculator withdrawalRefundRateCalculator;
    private final AuditLogRecorder auditLogRecorder;

    // SCRUM-63: 자퇴 예상 환불금 조회 (조회만, 저장 없음)
    public WithdrawalRefundEstimateResponseDTO estimateWithdrawalRefund(CurrentUser currentUser, Long tuitionBillId) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        BigDecimal rate = resolveWithdrawalRefundRate(tuitionBill);
        BigDecimal estimatedAmount = tuitionBill.getBillingAmount().multiply(rate);

        return new WithdrawalRefundEstimateResponseDTO(tuitionBill.getId(), tuitionBill.getBillingAmount(), rate, estimatedAmount);
    }

    // SCRUM-166: 자퇴 처리일 기준 환불률 적용 (동일 고지에 재요청 시 새로 만들지 않고 기존 REQUESTED 건의 비율만 갱신 - 비기능 #19)
    // Academic 호출 동안 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행한다(B3번).
    // findByTuitionBillIdAndRefundType()가 반환한 엔티티는 그 조회 자체의 트랜잭션이 끝나며 detach되므로,
    // 변경 후 반드시 save()를 다시 호출해야 반영된다(더티체킹에 기대지 않는다).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponseDTO applyWithdrawalRefundRate(CurrentUser currentUser, WithdrawalRefundRateRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        BigDecimal rate = resolveWithdrawalRefundRate(tuitionBill);
        BigDecimal amount = tuitionBill.getBillingAmount().multiply(rate);

        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .orElseGet(() -> new Refund(tuitionBill.getId(), RefundType.WITHDRAWAL, amount, rate, RefundStatus.REQUESTED));
        refund.updateRate(amount, rate); // 기존 건 재요청 시에도 최신 환불률로 갱신(비기능 #19)
        refund = refundRepository.save(refund);

        auditLogRecorder.record(currentUser.id(), AuditAction.REFUND_REQUESTED, "REFUND", refund.getId(),
                Map.of("tuitionBillId", tuitionBill.getId(), "amount", amount, "refundRate", rate), null);

        return RefundResponseDTO.from(refund);
    }

    // SCRUM-175: 가상계좌 환불 요청 (55에서 발급한 계좌를 166에서 만든 환불 요청에 연결)
    // 실제 입금 확인·토스 환불 접수 호출은 입금 검증 인프라(virtual_account_deposits)가 생기는 week-4에서 이어간다 -
    // week-2 시점에는 "이 계좌로 환불하겠다"는 연결까지만 한다(MY-PLAN_payment.md 7-4절).
    @Transactional
    public RefundResponseDTO requestVirtualAccountRefund(CurrentUser currentUser, VirtualAccountRefundRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        VirtualAccount virtualAccount = virtualAccountService.getByTuitionBillIdOrThrow(tuitionBill.getId());
        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .orElseThrow(() -> new RefundNotFoundException(
                        "먼저 자퇴 처리일 기준 환불률을 적용해야 합니다(PATCH /api/academic-status/withdrawal-refund-rate)."));

        refund.linkVirtualAccount(virtualAccount.getId());

        return RefundResponseDTO.from(refund);
    }

    private BigDecimal resolveWithdrawalRefundRate(TuitionBill tuitionBill) {
        AcademicWithdrawalHistoryResponse history = academicClient.findLatestWithdrawalHistory(tuitionBill.getStudentId());
        AcademicSemesterResponse semester = academicClient.findSemester(tuitionBill.getSemesterId());
        return withdrawalRefundRateCalculator.calculate(history.processedAt(), semester.startDate(), semester.endDate());
    }
}

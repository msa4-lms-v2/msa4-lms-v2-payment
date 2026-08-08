package com.msa4lmsv2payment.domain.refund.service;

import com.msa4lmsv2payment.domain.refund.WithdrawalRefundRateCalculator;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.AcademicWithdrawalHistoryResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private final RefundRepository refundRepository;
    private final TuitionBillService tuitionBillService;
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
    @Transactional
    public RefundResponseDTO applyWithdrawalRefundRate(CurrentUser currentUser, WithdrawalRefundRateRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        BigDecimal rate = resolveWithdrawalRefundRate(tuitionBill);
        BigDecimal amount = tuitionBill.getBillingAmount().multiply(rate);

        Refund refund = refundRepository.findByTuitionBillIdAndRefundType(tuitionBill.getId(), RefundType.WITHDRAWAL)
                .map(existing -> {
                    existing.updateRate(amount, rate);
                    return existing;
                })
                .orElseGet(() -> refundRepository.save(
                        new Refund(tuitionBill.getId(), RefundType.WITHDRAWAL, amount, rate, RefundStatus.REQUESTED)));

        auditLogRecorder.record(currentUser.id(), AuditAction.REFUND_REQUESTED, "REFUND", refund.getId(),
                Map.of("tuitionBillId", tuitionBill.getId(), "amount", amount, "refundRate", rate), null);

        return RefundResponseDTO.from(refund);
    }

    private BigDecimal resolveWithdrawalRefundRate(TuitionBill tuitionBill) {
        AcademicWithdrawalHistoryResponse history = academicClient.findLatestWithdrawalHistory(tuitionBill.getStudentId());
        AcademicSemesterResponse semester = academicClient.findSemester(tuitionBill.getSemesterId());
        return withdrawalRefundRateCalculator.calculate(history.processedAt(), semester.startDate(), semester.endDate());
    }
}

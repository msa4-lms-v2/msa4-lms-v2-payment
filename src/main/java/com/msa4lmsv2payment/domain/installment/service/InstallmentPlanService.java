package com.msa4lmsv2payment.domain.installment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentItemStatus;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanStatus;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanItemRepository;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanCreateRequestDTO;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanDecision;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanReviewRequestDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanItemResponseDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.InstallmentItemAlreadyPaidException;
import com.msa4lmsv2payment.global.error.InstallmentPlanAlreadyExistsException;
import com.msa4lmsv2payment.global.error.InstallmentPlanItemNotFoundException;
import com.msa4lmsv2payment.global.error.InstallmentPlanNotApprovedException;
import com.msa4lmsv2payment.global.error.InstallmentPlanNotFoundException;
import com.msa4lmsv2payment.global.error.RejectReasonRequiredException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstallmentPlanService {

    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentPlanItemRepository installmentPlanItemRepository;
    private final TuitionBillService tuitionBillService;
    private final ScholarshipService scholarshipService;
    private final InstallmentPlanRecorderService installmentPlanRecorder;
    private final AuditLogRecorder auditLogRecorder;

    // 회차 금액은 항상 서버가 실납부액(고지금액-장학금)을 회차 수로 나눠 계산한다 - 클라이언트가 회차 금액을 지정할 수 없다(위조 방지).
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InstallmentPlanResponseDTO createPlan(CurrentUser currentUser, InstallmentPlanCreateRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        if (installmentPlanRepository.findByTuitionBillId(tuitionBill.getId()).isPresent()) {
            throw new InstallmentPlanAlreadyExistsException("이미 분할납부 계획이 존재하는 고지입니다.");
        }

        BigDecimal actualPaymentAmount = scholarshipService.calculateAllocation(
                currentUser, new PaymentScholarshipAllocationRequestDTO(tuitionBill.getId())).actualPaymentAmount();

        InstallmentPlan saved = installmentPlanRecorder.saveWithAudit(currentUser.id(),
                new InstallmentPlan(tuitionBill.getId(), request.totalRounds()),
                buildItems(actualPaymentAmount, request.totalRounds(), tuitionBill.getDueDate()));

        return toResponse(saved);
    }

    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InstallmentPlanResponseDTO getPlan(CurrentUser currentUser, Long tuitionBillId) {
        tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        InstallmentPlan plan = getPlanByTuitionBillOrThrow(tuitionBillId);
        return toResponse(plan);
    }

    public InstallmentPlan getPlanByTuitionBillOrThrow(Long tuitionBillId) {
        return installmentPlanRepository.findByTuitionBillId(tuitionBillId)
                .orElseThrow(() -> new InstallmentPlanNotFoundException("해당 고지의 분할납부 계획을 찾을 수 없습니다."));
    }

    // ADMIN이 신청을 승인해야만 회차 결제가 가능하다 - 승인 전 신청만으로는 분할납부를 시작할 수 없다(사용자 확정 요구사항).
    @Transactional
    public InstallmentPlanResponseDTO reviewPlan(CurrentUser admin, Long planId, InstallmentPlanReviewRequestDTO request) {
        InstallmentPlan plan = installmentPlanRepository.findById(planId)
                .orElseThrow(() -> new InstallmentPlanNotFoundException("해당 분할납부 계획을 찾을 수 없습니다."));

        if (request.decision() == InstallmentPlanDecision.APPROVE) {
            plan.approve(admin.id());
        } else {
            if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                throw new RejectReasonRequiredException("반려 시 반려 사유는 필수입니다.");
            }
            plan.reject(admin.id(), request.rejectReason());
        }

        auditLogRecorder.record(admin.id(), AuditAction.INSTALLMENT_PLAN_REVIEWED, "INSTALLMENT_PLAN", plan.getId(),
                Map.of("decision", request.decision().name()), request.rejectReason());

        return toResponse(plan);
    }

    /**
     * 분할납부 - PaymentService가 결제창 연동 시 청구할 회차 금액을 알아야 할 때 이 공개 메서드를 거친다.
     * 결제 행을 만들기 전 조회 전용으로 쓰며, 이 시점에는 아직 결제와 연결하지 않는다(엔티티는 변경하지 않음).
     * 계획이 ACTIVE(승인됨)가 아니면 거부한다 - 신청만으로는 분할납부를 시작할 수 없다.
     */
    public InstallmentPlanItem getItemOrThrow(Long tuitionBillId, Long installmentPlanItemId) {
        InstallmentPlan plan = getPlanByTuitionBillOrThrow(tuitionBillId);
        if (plan.getStatus() != InstallmentPlanStatus.ACTIVE) {
            throw new InstallmentPlanNotApprovedException("승인된 분할납부 계획만 결제할 수 있습니다.");
        }
        InstallmentPlanItem item = installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId()).stream()
                .filter(it -> it.getId().equals(installmentPlanItemId))
                .findFirst()
                .orElseThrow(() -> new InstallmentPlanItemNotFoundException("해당 고지의 분할납부 회차를 찾을 수 없습니다."));
        if (item.getStatus() == InstallmentItemStatus.PAID) {
            throw new InstallmentItemAlreadyPaidException("이미 납부 완료된 회차입니다.");
        }
        return item;
    }

    /**
     * 결제 행 저장 직후, 그 결제를 이 회차에 연결한다(위 조회와 분리해 결제 ID가 생긴 뒤에만 연결하도록 강제).
     */
    @Transactional
    public void assignPaymentToItem(Long installmentPlanItemId, Long paymentId) {
        InstallmentPlanItem item = installmentPlanItemRepository.findById(installmentPlanItemId)
                .orElseThrow(() -> new InstallmentPlanItemNotFoundException("해당 분할납부 회차를 찾을 수 없습니다."));
        item.assignPayment(paymentId);
    }

    /**
     * PaymentResultRecorderService가 결제 성공을 저장하는 같은 트랜잭션에서 회차를 완료 처리할 때 이 공개 메서드를 거친다.
     * installmentPlanItemId가 null이면(일반 전액 결제) 아무 것도 하지 않는다.
     */
    @Transactional
    public void markItemPaid(Long installmentPlanItemId, Long paymentId) {
        if (installmentPlanItemId == null) {
            return;
        }
        InstallmentPlanItem item = installmentPlanItemRepository.findById(installmentPlanItemId)
                .orElseThrow(() -> new InstallmentPlanItemNotFoundException("해당 분할납부 회차를 찾을 수 없습니다."));
        if (!paymentId.equals(item.getPaymentId())) {
            return;
        }
        item.markPaid();

        long unpaidCount = installmentPlanItemRepository
                .countByInstallmentPlanIdAndStatus(item.getInstallmentPlanId(), InstallmentItemStatus.SCHEDULED);
        if (unpaidCount == 0) {
            installmentPlanRepository.findById(item.getInstallmentPlanId()).ifPresent(InstallmentPlan::complete);
        }
    }

    /**
     * 승인된(ACTIVE) 분할납부 계획이 있는 고지에 장학금이 변경되면, 아직 납부하지 않은 회차(SCHEDULED, OVERDUE) 금액을
     * 새 실납부액 기준으로 다시 나눈다. 이미 낸(PAID) 회차는 소급해 바꾸지 않고, 계획이 없거나 ACTIVE가 아니면 아무 것도 하지 않는다.
     * 호출부(장학금 변경 트랜잭션)와 별도 트랜잭션으로 실행될 수 있다 - ScholarshipService에 대한 역방향 의존을 피하기 위해
     * 이 메서드는 장학금 도메인 서비스를 직접 부르지 않고, 호출부가 이미 계산한 실납부액을 그대로 받는다.
     */
    @Transactional
    public void recalculateForScholarshipChange(Long actorId, Long tuitionBillId, BigDecimal newActualPaymentAmount) {
        InstallmentPlan plan = installmentPlanRepository.findByTuitionBillId(tuitionBillId).orElse(null);
        if (plan == null || plan.getStatus() != InstallmentPlanStatus.ACTIVE) {
            return;
        }

        List<InstallmentPlanItem> items = installmentPlanItemRepository.findByInstallmentPlanIdOrderByRoundNo(plan.getId());
        List<InstallmentPlanItem> unpaidItems = items.stream()
                .filter(item -> item.getStatus() != InstallmentItemStatus.PAID)
                .toList();
        if (unpaidItems.isEmpty()) {
            return;
        }

        BigDecimal paidTotal = items.stream()
                .filter(item -> item.getStatus() == InstallmentItemStatus.PAID)
                .map(InstallmentPlanItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingAmount = newActualPaymentAmount.subtract(paidTotal).max(BigDecimal.ZERO);

        List<BigDecimal> newAmounts = splitAmount(remainingAmount, unpaidItems.size());
        for (int i = 0; i < unpaidItems.size(); i++) {
            unpaidItems.get(i).updateAmount(newAmounts.get(i));
        }

        auditLogRecorder.record(actorId, AuditAction.INSTALLMENT_PLAN_RECALCULATED, "INSTALLMENT_PLAN", plan.getId(),
                Map.of("tuitionBillId", tuitionBillId, "remainingAmount", remainingAmount, "recalculatedRounds", unpaidItems.size()), null);
    }

    private List<InstallmentPlanItemDraft> buildItems(BigDecimal totalAmount, int totalRounds, LocalDate firstDueDate) {
        List<BigDecimal> amounts = splitAmount(totalAmount, totalRounds);

        List<InstallmentPlanItemDraft> drafts = new ArrayList<>();
        for (int round = 1; round <= totalRounds; round++) {
            drafts.add(new InstallmentPlanItemDraft(round, amounts.get(round - 1), firstDueDate.plusMonths(round - 1)));
        }
        return drafts;
    }

    // 총액을 count개로 나눠 마지막 몫이 나머지를 흡수하게 한다(1원 단위 절사 오차가 마지막 회차에만 몰림).
    private List<BigDecimal> splitAmount(BigDecimal totalAmount, int count) {
        BigDecimal baseAmount = totalAmount.divide(BigDecimal.valueOf(count), 0, RoundingMode.DOWN);
        BigDecimal allocatedSoFar = BigDecimal.ZERO;

        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            boolean isLast = i == count;
            BigDecimal amount = isLast ? totalAmount.subtract(allocatedSoFar) : baseAmount;
            allocatedSoFar = allocatedSoFar.add(amount);
            amounts.add(amount);
        }
        return amounts;
    }

    private InstallmentPlanResponseDTO toResponse(InstallmentPlan plan) {
        List<InstallmentPlanItemResponseDTO> items = installmentPlanItemRepository
                .findByInstallmentPlanIdOrderByRoundNo(plan.getId()).stream()
                .map(InstallmentPlanItemResponseDTO::from)
                .toList();
        return InstallmentPlanResponseDTO.from(plan, items);
    }
}

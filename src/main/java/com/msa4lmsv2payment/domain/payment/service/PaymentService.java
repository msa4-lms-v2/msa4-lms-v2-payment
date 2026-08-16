package com.msa4lmsv2payment.domain.payment.service;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlanItem;
import com.msa4lmsv2payment.domain.installment.service.InstallmentPlanService;
import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.global.error.PaymentAmountMismatchException;
import com.msa4lmsv2payment.global.error.PaymentNotFoundException;
import com.msa4lmsv2payment.global.error.PaymentResultMismatchException;
import com.msa4lmsv2payment.global.error.TossServiceUnavailableException;
import com.msa4lmsv2payment.domain.payment.repository.PaymentHistoryQueryRepository;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.payment.request.CheckoutSessionRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentAmountValidationRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentResultSyncRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentStatusRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PgPaymentRequestDTO;
import com.msa4lmsv2payment.domain.payment.response.CheckoutSessionResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentAmountValidationResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentHistoryResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentSummaryResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    // 토스페이먼츠 orderId 규칙(영문/숫자/-/_, 6~64자)을 항상 만족하도록 자리수가 작은 초기 결제 ID에서도 6자 미만이 되지 않는 접두어를 쓴다.
    private static final String ORDER_ID_PREFIX = "PAYMENT-";

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryQueryRepository paymentHistoryQueryRepository;
    private final TuitionBillService tuitionBillService;
    private final ScholarshipService scholarshipService;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentResultRecorder paymentResultRecorder;
    private final TuitionOverpaymentGuard tuitionOverpaymentGuard;
    private final InstallmentPlanService installmentPlanService;

    // SCRUM-51: 결제 금액 검증
    public PaymentAmountValidationResponseDTO validateAmount(CurrentUser currentUser, PaymentAmountValidationRequestDTO request) {
        BigDecimal expected = expectedAmount(currentUser, request.tuitionBillId());
        boolean valid = expected.compareTo(request.amount()) == 0;
        return new PaymentAmountValidationResponseDTO(valid, expected);
    }

    // SCRUM-111: 결제창 연동 - payments 행을 REQUESTED로 미리 만들고 체크아웃 데이터를 돌려준다.
    // 소유권 검증(getOwnedTuitionBillOrThrow)이 STUDENT 호출 시 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CheckoutSessionResponseDTO createCheckoutSession(CurrentUser currentUser, CheckoutSessionRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        Payment payment;
        if (request.installmentPlanItemId() != null) {
            // 분할납부 회차 결제 - 회차 금액은 클라이언트가 지정할 수 없고 서버가 계획에 저장된 금액을 그대로 쓴다(위조 방지).
            InstallmentPlanItem item = installmentPlanService.getItemOrThrow(tuitionBill.getId(), request.installmentPlanItemId());
            payment = paymentRepository.save(new Payment(
                    tuitionBill.getId(), tuitionBill.getStudentId(), item.getAmount(), request.method(),
                    PaymentStatus.REQUESTED, item.getId()));
            installmentPlanService.assignPaymentToItem(item.getId(), payment.getId());
        } else {
            BigDecimal amount = expectedAmount(currentUser, request.tuitionBillId());
            payment = paymentRepository.save(new Payment(
                    tuitionBill.getId(), tuitionBill.getStudentId(), amount, request.method(), PaymentStatus.REQUESTED));
        }

        return new CheckoutSessionResponseDTO(payment.getId(), ORDER_ID_PREFIX + payment.getId(), "등록금 납부", payment.getAmount());
    }

    // SCRUM-115: PG 결제 요청 - 51을 내부 재사용해 위조 금액을 거른 뒤 토스 confirm을 호출하고, 결과를 그 자리에서 저장한다(110 역할 포함).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponseDTO requestPgPayment(CurrentUser currentUser, PgPaymentRequestDTO request, String idempotencyKey) {
        Payment payment = findByOrderIdOrThrow(request.orderId());
        tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, payment.getTuitionBillId());

        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw new PaymentAmountMismatchException("결제 금액이 일치하지 않습니다.");
        }

        tuitionOverpaymentGuard.guard(payment.getTuitionBillId(), payment.getAmount());

        TossPaymentResponse tossResponse = tossPaymentsClient.confirmPayment(
                request.paymentKey(), request.orderId(), request.amount(), idempotencyKey);
        return applyPaymentResult(currentUser.id(), payment, tossResponse, request.orderId(), request.paymentKey());
    }

    // SCRUM-110: 결제 성공·실패 처리 - confirm 호출이 타임아웃됐을 때 ADMIN이 토스 실제 상태로 DB를 동기화하는 복구용.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponseDTO syncPaymentResult(CurrentUser admin, Long paymentId, PaymentResultSyncRequestDTO request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 세션을 찾을 수 없습니다: " + paymentId));
        TossPaymentResponse tossResponse = tossPaymentsClient.getPayment(request.paymentKey());
        return applyPaymentResult(admin.id(), payment, tossResponse, ORDER_ID_PREFIX + payment.getId(), request.paymentKey());
    }

    private PaymentResponseDTO applyPaymentResult(Long actorId, Payment payment, TossPaymentResponse tossResponse,
                                                   String expectedOrderId, String expectedPaymentKey) {
        validateTossResponse(payment, tossResponse, expectedOrderId, expectedPaymentKey);
        if (payment.isSucceeded()) {
            if (!tossResponse.isDone() || !payment.getPgTransactionId().equals(tossResponse.paymentKey())) {
                throw new PaymentResultMismatchException("완료된 결제 결과와 PG 조회 결과가 일치하지 않습니다.");
            }
            return PaymentResponseDTO.from(payment);
        }
        if (tossResponse.isDone()) {
            payment.succeed(tossResponse.paymentKey());
        } else {
            payment.fail();
        }
        Payment saved = paymentResultRecorder.saveWithAudit(actorId, payment, tossResponse.status());
        // 회차 완료 처리는 결제 저장과 별도 트랜잭션이다(recalculateTuitionStatus와 동일하게 클라이언트가 이어서 호출하는 흐름을 따름).
        if (saved.isSucceeded()) {
            installmentPlanService.markItemPaid(saved.getInstallmentPlanItemId(), saved.getId());
        }
        return PaymentResponseDTO.from(saved);
    }

    private void validateTossResponse(Payment payment, TossPaymentResponse response,
                                      String expectedOrderId, String expectedPaymentKey) {
        if (response == null || response.orderId() == null || response.paymentKey() == null
                || response.totalAmount() == null || response.status() == null) {
            throw new TossServiceUnavailableException("토스페이먼츠 결제 응답이 올바르지 않습니다.");
        }
        if (!expectedOrderId.equals(response.orderId())
                || !expectedPaymentKey.equals(response.paymentKey())
                || payment.getAmount().compareTo(BigDecimal.valueOf(response.totalAmount())) != 0) {
            throw new PaymentResultMismatchException("PG 결제 결과가 로컬 결제 정보와 일치하지 않습니다.");
        }
    }

    // SCRUM-112: 납부 상태 반영(쓰기) - SUCCEEDED 결제 합계로 tuition_bills.status를 재계산한다.
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recalculateTuitionStatus(CurrentUser currentUser, PaymentStatusRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());
        BigDecimal netDue = allocation(currentUser, tuitionBill.getId()).actualPaymentAmount();
        BigDecimal totalPaid = paymentRepository.sumSucceededAmount(tuitionBill.getId());

        TuitionBillStatus status;
        if (totalPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = TuitionBillStatus.UNPAID;
        } else if (totalPaid.compareTo(netDue) >= 0) {
            status = TuitionBillStatus.PAID;
        } else {
            status = TuitionBillStatus.PARTIAL;
        }
        tuitionBillService.changeStatus(tuitionBill.getId(), status);
    }

    /**
     * SCRUM-114 - 다른 도메인(document 등)이 납부 완료 여부를 확인해야 할 때 이 공개 메서드를 거친다(B1번 패키지 경계).
     */
    public boolean hasSucceededPayment(Long tuitionBillId) {
        return !paymentRepository.findByTuitionBillIdAndStatus(tuitionBillId, PaymentStatus.SUCCEEDED).isEmpty();
    }

    // SCRUM-113: 납부 현황 반영(읽기)
    public PaymentSummaryResponseDTO getPaymentSummary(CurrentUser currentUser, Long tuitionBillId) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        PaymentScholarshipAllocationResponseDTO allocation = allocation(currentUser, tuitionBillId);
        BigDecimal totalPaid = paymentRepository.sumSucceededAmount(tuitionBillId);
        BigDecimal remaining = allocation.actualPaymentAmount().subtract(totalPaid).max(BigDecimal.ZERO);

        return new PaymentSummaryResponseDTO(
                tuitionBillId, tuitionBill.getBillingAmount(), allocation.totalScholarshipAmount(),
                totalPaid, remaining, tuitionBill.getStatus());
    }

    // 학생 본인의 일괄납부/분할납부 이력 조회(등록금 신청 내역 화면용)
    public List<PaymentHistoryResponseDTO> getMyPaymentHistory(CurrentUser currentUser, PaymentStatus status) {
        return paymentHistoryQueryRepository.findMyHistory(currentUser.id(), status);
    }

    private Payment findByOrderIdOrThrow(String orderId) {
        return paymentRepository.findById(parsePaymentId(orderId))
                .orElseThrow(() -> new PaymentNotFoundException("결제 세션을 찾을 수 없습니다: " + orderId));
    }

    private Long parsePaymentId(String orderId) {
        if (orderId == null || !orderId.startsWith(ORDER_ID_PREFIX)) {
            throw new PaymentNotFoundException("잘못된 orderId 형식입니다: " + orderId);
        }
        try {
            return Long.valueOf(orderId.substring(ORDER_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new PaymentNotFoundException("잘못된 orderId 형식입니다: " + orderId);
        }
    }

    private BigDecimal expectedAmount(CurrentUser currentUser, Long tuitionBillId) {
        return allocation(currentUser, tuitionBillId).actualPaymentAmount();
    }

    private PaymentScholarshipAllocationResponseDTO allocation(CurrentUser currentUser, Long tuitionBillId) {
        return scholarshipService.calculateAllocation(currentUser, new PaymentScholarshipAllocationRequestDTO(tuitionBillId));
    }
}

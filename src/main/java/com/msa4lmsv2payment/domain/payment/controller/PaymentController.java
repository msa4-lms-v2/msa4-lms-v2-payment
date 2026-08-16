package com.msa4lmsv2payment.domain.payment.controller;

import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
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
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.global.config.OpenApiConfig;
import com.msa4lmsv2payment.global.idempotency.IdempotencyService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Tag(name = "Payment", description = "PG 결제·납부 상태 반영")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final String ENDPOINT_PAYMENTS_CONFIRM = "/api/payment/payments/confirm";

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "결제 금액 검증", description = "서버가 계산한 실납부액과 클라이언트가 보낸 금액이 일치하는지 비교만 하는 순수 조회(위조 요청 사전 차단용). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 성공(valid 필드로 일치 여부 반환)"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping("/api/payment/payment-amount-validation")
    public GlobalRes<PaymentAmountValidationResponseDTO> validateAmount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentAmountValidationRequestDTO request
    ) {
        return GlobalRes.success(paymentService.validateAmount(currentUser, request));
    }

    @Operation(summary = "결제창 연동", description = "payments 행을 REQUESTED 상태로 미리 만들고 결제창에 넘길 orderId를 발급한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "세션 생성 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/payments")
    public GlobalRes<CheckoutSessionResponseDTO> createCheckoutSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid CheckoutSessionRequestDTO request
    ) {
        return GlobalRes.success(paymentService.createCheckoutSession(currentUser, request));
    }

    // API_SPEC.md 2.1절 - 결제 API는 Idempotency-Key 필수(비멱등 PG 승인 재시도 대비, code_convention.md B17번).
    @Operation(summary = "PG 결제 승인", description = """
            결제창 연동에서 생성한 REQUESTED 거래를 서버 금액과 다시 대조한 뒤 토스 confirm을 호출한다.
            토스 응답의 orderId, paymentKey, totalAmount를 로컬 거래와 모두 비교하고 일치할 때만 결과와 감사 로그를 원자적으로 저장한다.
            SUCCEEDED는 종결 상태라 이후 실패 응답으로 역전하지 않는다. STUDENT 본인 / ADMIN 관리 범위.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료 또는 완료된 동일 멱등 요청의 저장 응답 재생",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalRes.class),
                            examples = @ExampleObject(name = "승인 성공", value = """
                                    {"code":"00","message":"SUCCESS","data":{"id":10,"tuitionBillId":1,"amount":4200000,"method":"CARD","pgTransactionId":"tgen_20260813_001","status":"SUCCEEDED"}}
                                    """))),
            @ApiResponse(responseCode = "400", ref = OpenApiConfig.INVALID_PARAMETER_RESPONSE_REF),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(responseCode = "409", ref = OpenApiConfig.DUPLICATE_RESPONSE_REF),
            @ApiResponse(responseCode = "503", ref = OpenApiConfig.DEPENDENCY_UNAVAILABLE_RESPONSE_REF)
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping(ENDPOINT_PAYMENTS_CONFIRM)
    public GlobalRes<PaymentResponseDTO> requestPgPayment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = """
                    1~100자의 중복 요청 방지 키. 요청자, endpoint, payload가 모두 같은 완료 요청은 저장된 응답을 재생하며 토스 confirm을 다시 호출하지 않는다.
                    다른 요청에 키를 재사용하거나 동일 요청이 아직 처리 중이면 409 E11을 반환한다.
                    """, required = true, schema = @Schema(minLength = 1, maxLength = 100, example = "pay-confirm-20260813-0001"))
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PgPaymentRequestDTO request
    ) {
        Optional<PaymentResponseDTO> replay = idempotencyService.verifyAndReserve(
                idempotencyKey, currentUser.id(), ENDPOINT_PAYMENTS_CONFIRM, request, PaymentResponseDTO.class);
        if (replay.isPresent()) {
            return GlobalRes.success(replay.orElseThrow());
        }
        PaymentResponseDTO response = paymentService.requestPgPayment(currentUser, request, idempotencyKey);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalRes.success(response);
    }

    @Operation(summary = "결제 결과 수동 동기화", description = "PG 승인 결과가 불명확할 때 ADMIN이 토스 단건 조회 결과로 로컬 거래를 복구한다. orderId, paymentKey, totalAmount를 모두 대조하고 SUCCEEDED 종결 상태를 보호한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동기화 완료(SUCCEEDED 또는 FAILED)"),
            @ApiResponse(responseCode = "403", description = "ADMIN 아님"),
            @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(responseCode = "409", ref = OpenApiConfig.DUPLICATE_RESPONSE_REF),
            @ApiResponse(responseCode = "503", ref = OpenApiConfig.DEPENDENCY_UNAVAILABLE_RESPONSE_REF)
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/payment/payments/{paymentId}/reconciliation")
    public GlobalRes<PaymentResponseDTO> syncPaymentResult(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long paymentId,
            @RequestBody @Valid PaymentResultSyncRequestDTO request
    ) {
        return GlobalRes.success(paymentService.syncPaymentResult(admin, paymentId, request));
    }

    @Operation(summary = "납부 상태 반영", description = "SUCCEEDED 결제 합계와 실납부액을 비교해 등록금 고지 상태(UNPAID/PARTIAL/PAID)를 재계산한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반영 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PatchMapping("/api/payment/payment-status")
    public GlobalRes<Void> recalculateTuitionStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentStatusRequestDTO request
    ) {
        paymentService.recalculateTuitionStatus(currentUser, request);
        return GlobalRes.success();
    }

    @Operation(summary = "납부 현황 반영", description = "고지금액·장학금 합계·누적 납부액·잔액·상태를 한 번에 조회한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/payment-summary")
    public GlobalRes<PaymentSummaryResponseDTO> getPaymentSummary(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(paymentService.getPaymentSummary(currentUser, tuitionBillId));
    }

    @Operation(summary = "학생 본인 납부 이력 조회", description = "학생 본인의 일괄납부/분할납부 결제 이력을 학기·구분·상태와 함께 반환한다. status로 필터링할 수 있다. STUDENT 본인 범위.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payment/me/payment-history")
    public GlobalRes<List<PaymentHistoryResponseDTO>> getMyPaymentHistory(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) PaymentStatus status
    ) {
        return GlobalRes.success(paymentService.getMyPaymentHistory(currentUser, status));
    }
}

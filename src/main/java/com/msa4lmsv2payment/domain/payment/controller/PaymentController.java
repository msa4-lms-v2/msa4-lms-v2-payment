package com.msa4lmsv2payment.domain.payment.controller;

import com.msa4lmsv2payment.domain.payment.request.CheckoutSessionRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentAmountValidationRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentResultSyncRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PaymentStatusRequestDTO;
import com.msa4lmsv2payment.domain.payment.request.PgPaymentRequestDTO;
import com.msa4lmsv2payment.domain.payment.response.CheckoutSessionResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentAmountValidationResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentResponseDTO;
import com.msa4lmsv2payment.domain.payment.response.PaymentSummaryResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.global.idempotency.IdempotencyService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Tag(name = "Payment", description = "PG 결제·납부 상태 반영")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private static final String ENDPOINT_PG_REQUESTS = "/api/payment/pg-requests";

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
    @PostMapping("/api/payment/checkout-session")
    public GlobalRes<CheckoutSessionResponseDTO> createCheckoutSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid CheckoutSessionRequestDTO request
    ) {
        return GlobalRes.success(paymentService.createCheckoutSession(currentUser, request));
    }

    // API_SPEC.md 2.1절 - 결제 API는 Idempotency-Key 필수(비멱등 PG 승인 재시도 대비, code_convention.md B17번).
    @Operation(summary = "PG 결제 요청", description = "결제 금액 검증을 내부 재사용해 위조 금액을 거른 뒤 토스 confirm을 호출하고, 성공·실패 결과를 그 자리에서 저장한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "confirm 처리 완료(SUCCEEDED 또는 FAILED)"),
            @ApiResponse(responseCode = "400", description = "결제 금액 불일치"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 결제 세션(orderId)"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key 재사용 충돌"),
            @ApiResponse(responseCode = "503", description = "토스페이먼츠 연결 실패(TOSS_SECRET_KEY 미설정 포함)")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping(ENDPOINT_PG_REQUESTS)
    public GlobalRes<PaymentResponseDTO> requestPgPayment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "중복 요청 방지 키. 동일 키+동일 요청 재시도는 통과, 다른 요청에 재사용하면 409", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PgPaymentRequestDTO request
    ) {
        Optional<PaymentResponseDTO> replay = idempotencyService.verifyAndReserve(
                idempotencyKey, currentUser.id(), ENDPOINT_PG_REQUESTS, request, PaymentResponseDTO.class);
        if (replay.isPresent()) {
            return GlobalRes.success(replay.orElseThrow());
        }
        PaymentResponseDTO response = paymentService.requestPgPayment(currentUser, request, idempotencyKey);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalRes.success(response);
    }

    @Operation(summary = "결제 성공·실패 처리", description = "PG 결제 요청의 confirm 호출이 타임아웃됐을 때 ADMIN이 토스 실제 상태(단건 조회)로 DB를 동기화하는 복구 전용 경로. ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동기화 완료(SUCCEEDED 또는 FAILED)"),
            @ApiResponse(responseCode = "403", description = "ADMIN 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 결제 세션(orderId)"),
            @ApiResponse(responseCode = "503", description = "토스페이먼츠 연결 실패(존재하지 않는 결제 포함)")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/payment/payment-results")
    public GlobalRes<PaymentResponseDTO> syncPaymentResult(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid PaymentResultSyncRequestDTO request
    ) {
        return GlobalRes.success(paymentService.syncPaymentResult(admin, request));
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
}

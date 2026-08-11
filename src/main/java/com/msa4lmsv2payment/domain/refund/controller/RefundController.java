package com.msa4lmsv2payment.domain.refund.controller;

import com.msa4lmsv2payment.domain.refund.request.RefundRetryRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
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

@Tag(name = "Refund", description = "자퇴 환불·가상계좌 환불·재시도")
@RestController
@RequiredArgsConstructor
public class RefundController {

    private static final String ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS = "/api/refunds/virtual-account-requests";
    private static final String ENDPOINT_RETRY = "/api/refunds/retry";

    private final RefundService refundService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "자퇴 예상 환불금 조회", description = "자퇴 처리일 기준 환불률표를 적용한 예상 환불금을 조회만 한다(저장 없음). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지·자퇴 이력")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/academic-status/withdrawal-refund-estimate")
    public GlobalRes<WithdrawalRefundEstimateResponseDTO> getWithdrawalRefundEstimate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(refundService.estimateWithdrawalRefund(currentUser, tuitionBillId));
    }

    @Operation(summary = "자퇴 처리일 기준 환불률 적용", description = "예상 환불금을 실제 환불 요청(REQUESTED)으로 저장한다. 동일 고지 재요청 시 새 행 대신 기존 건의 비율만 갱신한다(비기능 #19).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적용 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지·자퇴 이력")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PatchMapping("/api/academic-status/withdrawal-refund-rate")
    public GlobalRes<RefundResponseDTO> applyWithdrawalRefundRate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid WithdrawalRefundRateRequestDTO request
    ) {
        return GlobalRes.success(refundService.applyWithdrawalRefundRate(currentUser, request));
    }

    // API_SPEC.md 2.1절 - Idempotency-Key는 결제·신청류 API에 필수. 동일 키+동일 요청 재시도는 통과시키고
    // (하위 로직이 upsert 성격이라 재실행해도 결과가 같다), 다른 요청에 같은 키를 재사용하면 거부한다(M2번).
    @Operation(summary = "가상계좌 환불 요청", description = "발급된 가상계좌를 환불률 적용 건에 연결한다. 실제 입금 확인·토스 환불 접수는 week-4 이후 범위. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "연결 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "발급된 계좌 또는 선행 환불률 적용 없음"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key 재사용 충돌")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS)
    public GlobalRes<RefundResponseDTO> requestVirtualAccountRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "중복 요청 방지 키. 동일 키+동일 요청 재시도는 통과, 다른 요청에 재사용하면 409", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid VirtualAccountRefundRequestDTO request
    ) {
        idempotencyService.verifyAndReserve(idempotencyKey, currentUser.id(), ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS, request);
        RefundResponseDTO response = refundService.requestVirtualAccountRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey);
        return GlobalRes.success(response);
    }

    @Operation(summary = "실패한 환불 재시도", description = "FAILED 상태의 환불만 재시도할 수 있다. MAX_RETRY_ATTEMPTS(3회)를 넘으면 최종 실패로 보고 거부한다(비기능 #26). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재시도 성공(RETRYING 전환)"),
            @ApiResponse(responseCode = "400", description = "FAILED 상태가 아니거나 재시도 횟수 초과(최종 실패)"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 환불 요청"),
            @ApiResponse(responseCode = "409", description = "Idempotency-Key 재사용 충돌")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping(ENDPOINT_RETRY)
    public GlobalRes<RefundResponseDTO> retryFailedRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "중복 요청 방지 키. 동일 키+동일 요청 재시도는 통과, 다른 요청에 재사용하면 409", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid RefundRetryRequestDTO request
    ) {
        idempotencyService.verifyAndReserve(idempotencyKey, currentUser.id(), ENDPOINT_RETRY, request);
        RefundResponseDTO response = refundService.retryFailedRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey);
        return GlobalRes.success(response);
    }
}

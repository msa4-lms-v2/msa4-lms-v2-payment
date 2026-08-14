package com.msa4lmsv2payment.domain.refund.controller;

import com.msa4lmsv2payment.domain.refund.request.RefundRetryRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Tag(name = "Refund", description = "자퇴 환불·가상계좌 환불·재시도")
@RestController
@RequiredArgsConstructor
public class RefundController {

    private static final String ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS = "/api/payment/refunds/virtual-account-requests";
    private static final String ENDPOINT_RETRY = "/api/payment/refunds/retry";

    private final RefundService refundService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "자퇴 예상 환불금 조회", description = "자퇴 처리일 기준 환불률표를 적용한 예상 환불금을 조회만 한다(저장 없음). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지·자퇴 이력")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/refunds/withdrawal-estimate")
    public GlobalRes<WithdrawalRefundEstimateResponseDTO> getWithdrawalRefundEstimate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId,
            @RequestParam Long withdrawalId
    ) {
        return GlobalRes.success(refundService.estimateWithdrawalRefund(currentUser, tuitionBillId, withdrawalId));
    }

    @Operation(summary = "자퇴 처리일 기준 환불률 적용", description = "자퇴 이력과 학기 일정을 Academic에서 조회해 환불률과 금액을 계산하고 REQUESTED 환불로 저장한다. 동일 고지의 미완료 요청은 갱신하지만 SUCCEEDED 환불의 금액과 비율은 변경하지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "적용 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "400", ref = OpenApiConfig.INVALID_PARAMETER_RESPONSE_REF),
            @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(responseCode = "503", ref = OpenApiConfig.DEPENDENCY_UNAVAILABLE_RESPONSE_REF)
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PatchMapping("/api/payment/refunds/withdrawal-rate")
    public GlobalRes<RefundResponseDTO> applyWithdrawalRefundRate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid WithdrawalRefundRateRequestDTO request
    ) {
        return GlobalRes.success(refundService.applyWithdrawalRefundRate(currentUser, request));
    }

    // 완료된 동일 멱등 요청은 저장된 응답을 재생하며 환불 연결 로직을 다시 실행하지 않는다.
    @Operation(summary = "가상계좌 환불 요청", description = "발급된 가상계좌를 환불률 적용 건에 연결한다. 실제 입금 확인·토스 환불 접수는 week-4 이후 범위. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "연결 성공 또는 완료된 동일 멱등 요청의 저장 응답 재생",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalRes.class),
                            examples = @ExampleObject(name = "환불 연결 성공", value = """
                                    {"code":"00","message":"SUCCESS","data":{"id":5,"tuitionBillId":1,"withdrawalId":1,"refundType":"WITHDRAWAL","amount":3499860,"refundRate":0.8333,"status":"REQUESTED","retryCount":0}}
                                    """))),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(responseCode = "409", ref = OpenApiConfig.DUPLICATE_RESPONSE_REF)
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS)
    public GlobalRes<RefundResponseDTO> requestVirtualAccountRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = """
                    1~100자의 중복 요청 방지 키. 요청자, endpoint, payload가 모두 같은 완료 요청은 저장된 응답을 재생하며 환불 연결을 다시 실행하지 않는다.
                    다른 요청에 키를 재사용하거나 동일 요청이 아직 처리 중이면 409 E11을 반환한다.
                    """, required = true, schema = @Schema(minLength = 1, maxLength = 100, example = "refund-link-20260813-0001"))
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid VirtualAccountRefundRequestDTO request
    ) {
        Optional<RefundResponseDTO> replay = idempotencyService.verifyAndReserve(
                idempotencyKey, currentUser.id(), ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS, request, RefundResponseDTO.class);
        if (replay.isPresent()) {
            return GlobalRes.success(replay.orElseThrow());
        }
        RefundResponseDTO response = refundService.requestVirtualAccountRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalRes.success(response);
    }

    @Operation(summary = "실패한 환불 재시도", description = "FAILED 상태의 환불을 관리자가 수동 재처리한다. MAX_RETRY_ATTEMPTS(3회)를 넘으면 최종 실패로 보고 거부한다(비기능 #26).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재시도 성공(RETRYING 전환)"),
            @ApiResponse(responseCode = "400", ref = OpenApiConfig.INVALID_PARAMETER_RESPONSE_REF),
            @ApiResponse(responseCode = "403", description = "ADMIN 역할이 아님"),
            @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND_RESPONSE_REF),
            @ApiResponse(responseCode = "409", ref = OpenApiConfig.DUPLICATE_RESPONSE_REF),
            @ApiResponse(responseCode = "503", ref = OpenApiConfig.DEPENDENCY_UNAVAILABLE_RESPONSE_REF)
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(ENDPOINT_RETRY)
    public GlobalRes<RefundResponseDTO> retryFailedRefund(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = """
                    1~100자의 중복 요청 방지 키. 요청자, endpoint, payload가 모두 같은 완료 요청은 저장된 응답을 재생하며 재시도를 다시 실행하지 않는다.
                    다른 요청에 키를 재사용하거나 동일 요청이 아직 처리 중이면 409 E11을 반환한다.
                    """, required = true, schema = @Schema(minLength = 1, maxLength = 100, example = "refund-retry-20260813-0001"))
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid RefundRetryRequestDTO request
    ) {
        Optional<RefundResponseDTO> replay = idempotencyService.verifyAndReserve(
                idempotencyKey, currentUser.id(), ENDPOINT_RETRY, request, RefundResponseDTO.class);
        if (replay.isPresent()) {
            return GlobalRes.success(replay.orElseThrow());
        }
        RefundResponseDTO response = refundService.retryFailedRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalRes.success(response);
    }
}

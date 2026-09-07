package com.msa4lmsv2payment.domain.refund.controller;

import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.request.PgCancelRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.RefundExecuteRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.RefundRetryRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.VirtualAccountRefundRequestDTO;
import com.msa4lmsv2payment.domain.refund.request.WithdrawalRefundRateRequestDTO;
import com.msa4lmsv2payment.domain.refund.response.RefundResponseDTO;
import com.msa4lmsv2payment.domain.refund.response.WithdrawalRefundEstimateResponseDTO;
import com.msa4lmsv2payment.domain.refund.service.RefundService;
import com.msa4lmsv2payment.global.config.openapi.CustomApiResponse;
import com.msa4lmsv2payment.global.idempotency.IdempotencyService;
import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Tag(name = "Refund", description = "자퇴 환불·가상계좌 환불·재시도")
@RestController
@RequiredArgsConstructor
public class RefundController {

    private static final String ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS = "/api/payment/refunds/virtual-account-requests";
    private static final String ENDPOINT_RETRY = "/api/payment/refunds/retry";
    private static final String ENDPOINT_PG_CANCEL_REQUESTS = "/api/payment/refunds/pg-cancel-requests";
    private static final String ENDPOINT_EXECUTE = "/api/payment/refunds/{refundId}/execute";

    private final RefundService refundService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "자퇴 예상 환불금 조회", description = "자퇴 처리일 기준 환불률표를 적용한 예상 환불금을 조회만 한다(저장 없음). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/refunds/withdrawal-estimate")
    public GlobalResponseDTO<WithdrawalRefundEstimateResponseDTO> getWithdrawalRefundEstimate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId,
            @RequestParam Long withdrawalId
    ) {
        return GlobalResponseDTO.success(refundService.estimateWithdrawalRefund(currentUser, tuitionBillId, withdrawalId));
    }

    @Operation(summary = "자퇴 처리일 기준 환불률 적용", description = """
            자퇴 이력과 학기 일정을 Academic에서 조회해 환불률과 금액을 계산하고 REQUESTED 환불로 저장한다.
            동일 고지의 미완료 요청은 갱신하지만 SUCCEEDED 환불의 금액과 비율은 변경하지 않는다.
            Academic 스냅샷에 자퇴 건이 아직 반영되지 않았으면 계산을 보류하고 PENDING_ACADEMIC_VERIFICATION으로
            저장한 뒤 202를 반환한다 - 자동 재검증 스케줄러가 주기적으로 다시 시도한다.
            """)
    @ApiResponse(responseCode = "200", description = "적용 성공")
    @ApiResponse(responseCode = "202", description = "Academic 스냅샷 미반영으로 PENDING_ACADEMIC_VERIFICATION 보류 저장, 자동 재검증 예정")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.INVALID_PARAMETER,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.MANUAL_REVIEW_REQUIRED, CustomResponseCode.DEPENDENCY_UNAVAILABLE})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PatchMapping("/api/payment/refunds/withdrawal-rate")
    public ResponseEntity<GlobalResponseDTO<RefundResponseDTO>> applyWithdrawalRefundRate(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid WithdrawalRefundRateRequestDTO request
    ) {
        RefundResponseDTO response = refundService.applyWithdrawalRefundRate(currentUser, request);
        HttpStatus status = response.status() == RefundStatus.PENDING_ACADEMIC_VERIFICATION ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(GlobalResponseDTO.success(response));
    }

    // 완료된 동일 멱등 요청은 저장된 응답을 재생하며 환불 연결 로직을 다시 실행하지 않는다.
    @Operation(summary = "가상계좌 환불 요청", description = "발급된 가상계좌를 환불률 적용 건에 연결한다. 실제 입금 확인·토스 환불 접수는 이후 범위. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "201", description = "연결 성공 또는 완료된 동일 멱등 요청의 저장 응답 재생",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GlobalResponseDTO.class),
                    examples = @ExampleObject(name = "환불 연결 성공", value = """
                            {"code":"00","message":"SUCCESS","data":{"id":5,"tuitionBillId":1,"withdrawalId":1,"refundType":"WITHDRAWAL","amount":3499860,"refundRate":0.8333,"status":"REQUESTED","retryCount":0}}
                            """)))
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(ENDPOINT_VIRTUAL_ACCOUNT_REQUESTS)
    public GlobalResponseDTO<RefundResponseDTO> requestVirtualAccountRefund(
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
            return GlobalResponseDTO.success(replay.orElseThrow());
        }
        RefundResponseDTO response = refundService.requestVirtualAccountRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalResponseDTO.success(response);
    }

    @Operation(summary = "실패한 환불 재시도", description = "FAILED 상태의 환불을 관리자가 수동 재처리한다. MAX_RETRY_ATTEMPTS(3회)를 넘으면 최종 실패로 보고 거부한다.")
    @ApiResponse(responseCode = "200", description = "재시도 성공(RETRYING 전환)")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA, CustomResponseCode.DEPENDENCY_UNAVAILABLE})
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(ENDPOINT_RETRY)
    public GlobalResponseDTO<RefundResponseDTO> retryFailedRefund(
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
            return GlobalResponseDTO.success(replay.orElseThrow());
        }
        RefundResponseDTO response = refundService.retryFailedRefund(currentUser, request);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalResponseDTO.success(response);
    }

    @Operation(summary = "환불 이력 조회", description = "등록금 고지 1건의 환불 요청 이력을 최신순으로 조회한다(자퇴·가상계좌 초과입금·카드취소 전체). STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/refunds")
    public GlobalResponseDTO<List<RefundResponseDTO>> listRefunds(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalResponseDTO.success(refundService.listRefunds(currentUser, tuitionBillId));
    }

    @Operation(summary = "카드 결제 취소 요청", description = """
            성공한 카드 결제를 취소 대상으로 등록한다. amount를 비우면 이미 취소된 금액을 뺀 전액, 지정하면 그만큼만(부분취소) REQUESTED로 저장한다.
            같은 결제에 재요청하면 새로 만들지 않고 기존 REQUESTED 건의 금액만 갱신한다. 실제 토스 취소 호출은 이 요청이 아니라 /execute에서 한다. ADMIN 전용.
            """)
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(ENDPOINT_PG_CANCEL_REQUESTS)
    public GlobalResponseDTO<RefundResponseDTO> createPgCancelRefund(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid PgCancelRefundRequestDTO request
    ) {
        return GlobalResponseDTO.success(refundService.createPgCancelRefund(admin, request));
    }

    @Operation(summary = "환불 실행", description = """
            REQUESTED/RETRYING 상태의 환불을 실제 토스 취소 API로 실행한다. FAILED 상태는 재시도 횟수(3회) 안에서 자동으로 RETRYING 전환 후 실행한다.
            WITHDRAWAL/EXCESS_DEPOSIT(가상계좌 환불)는 refundBankCode/refundAccountNumber/refundHolderName이 필수다. PG_CANCEL(카드)은 비운다. ADMIN 전용.
            """)
    @ApiResponse(responseCode = "200", description = "실행 완료(SUCCEEDED 또는 FAILED) 또는 완료된 동일 멱등 요청의 저장 응답 재생")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA, CustomResponseCode.DEPENDENCY_UNAVAILABLE})
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(ENDPOINT_EXECUTE)
    public GlobalResponseDTO<RefundResponseDTO> executeRefund(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long refundId,
            @Parameter(description = """
                    1~100자의 중복 요청 방지 키. 이 값을 토스 취소 호출의 Idempotency-Key로도 그대로 사용해 같은 취소가 중복 처리되지 않게 한다.
                    """, required = true, schema = @Schema(minLength = 1, maxLength = 100, example = "refund-execute-20260907-0001"))
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid RefundExecuteRequestDTO request
    ) {
        Optional<RefundResponseDTO> replay = idempotencyService.verifyAndReserve(
                idempotencyKey, admin.id(), ENDPOINT_EXECUTE, request, RefundResponseDTO.class);
        if (replay.isPresent()) {
            return GlobalResponseDTO.success(replay.orElseThrow());
        }
        RefundResponseDTO response = refundService.executeRefund(admin, refundId, request, idempotencyKey);
        idempotencyService.markCompleted(idempotencyKey, response);
        return GlobalResponseDTO.success(response);
    }
}

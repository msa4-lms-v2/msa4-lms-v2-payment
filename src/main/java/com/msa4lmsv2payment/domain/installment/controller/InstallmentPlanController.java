package com.msa4lmsv2payment.domain.installment.controller;

import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanCreateRequestDTO;
import com.msa4lmsv2payment.domain.installment.request.InstallmentPlanReviewRequestDTO;
import com.msa4lmsv2payment.domain.installment.response.InstallmentPlanResponseDTO;
import com.msa4lmsv2payment.domain.installment.service.InstallmentPlanService;
import com.msa4lmsv2payment.global.config.openapi.CustomApiResponse;
import com.msa4lmsv2payment.global.response.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Installment", description = "등록금 분할납부 계획")
@RestController
@RequiredArgsConstructor
public class InstallmentPlanController {

    private final InstallmentPlanService installmentPlanService;

    @Operation(summary = "분할납부 신청", description = """
            등록금 고지 1건에 대해 회차별 납부 계획을 신청한다. 회차 금액은 실납부액(고지금액-장학금)을 회차 수로 나눠 서버가 계산하며,
            클라이언트가 회차 금액을 지정할 수 없다. 고지 1건당 신청은 하나만 만들 수 있다.
            신청 상태(REQUESTED)로 생성되며, ADMIN이 승인(ACTIVE)해야만 회차 결제를 시작할 수 있다. STUDENT 본인 / ADMIN 관리 범위.
            """)
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/installment-plans")
    public GlobalResponseDTO<InstallmentPlanResponseDTO> createInstallmentPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid InstallmentPlanCreateRequestDTO request
    ) {
        return GlobalResponseDTO.success(installmentPlanService.createPlan(currentUser, request));
    }

    @Operation(summary = "분할납부 계획 조회", description = "등록금 고지 1건의 분할납부 신청·계획과 회차별 상태를 조회한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/installment-plans")
    public GlobalResponseDTO<InstallmentPlanResponseDTO> getInstallmentPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalResponseDTO.success(installmentPlanService.getPlan(currentUser, tuitionBillId));
    }

    @Operation(summary = "분할납부 신청 심사", description = "ADMIN이 분할납부 신청을 승인·반려한다. 승인해야만(ACTIVE) 학생이 회차 결제를 시작할 수 있다.")
    @ApiResponse(responseCode = "200", description = "심사 완료")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/payment/installment-plans/{planId}/review")
    public GlobalResponseDTO<InstallmentPlanResponseDTO> reviewInstallmentPlan(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long planId,
            @RequestBody @Valid InstallmentPlanReviewRequestDTO request
    ) {
        return GlobalResponseDTO.success(installmentPlanService.reviewPlan(admin, planId, request));
    }
}

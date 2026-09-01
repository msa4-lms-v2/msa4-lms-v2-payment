package com.msa4lmsv2payment.domain.scholarship.controller;

import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.request.ScholarshipDiscountRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.MyScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payment", description = "장학금 감면·실납부액 계산")
@RestController
@RequiredArgsConstructor
public class ScholarshipController {

    private final ScholarshipService scholarshipService;

    @Operation(summary = "장학금 감면·면제 적용", description = "ADMIN이 등록금 고지에 장학금을 적용한다. 고지 금액을 초과하는 감면은 거부된다.")
    @ApiResponse(responseCode = "201", description = "적용 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.INVALID_PARAMETER})
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/scholarship-discounts")
    public GlobalResponseDTO<ScholarshipResponseDTO> applyScholarshipDiscount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid ScholarshipDiscountRequestDTO request
    ) {
        return GlobalResponseDTO.success(scholarshipService.applyScholarshipDiscount(currentUser, request));
    }

    @Operation(summary = "실제 납부액과 장학금 구분", description = "고지 금액에서 적용된 장학금 합계를 뺀 실납부액을 계산한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "200", description = "계산 성공")
    @CustomApiResponse({CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @PostMapping("/api/payment/payment-scholarship-allocation")
    public GlobalResponseDTO<PaymentScholarshipAllocationResponseDTO> getPaymentScholarshipAllocation(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentScholarshipAllocationRequestDTO request
    ) {
        return GlobalResponseDTO.success(scholarshipService.calculateAllocation(currentUser, request));
    }

    @Operation(summary = "내 장학금 수혜 내역", description = "STUDENT 본인이 받은 모든 학기의 장학금을 학기 ID와 함께 조회한다. 학기 선택 화면에서 종류·금액을 보여주는 용도.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payment/me/scholarships")
    public GlobalResponseDTO<List<MyScholarshipResponseDTO>> getMyScholarships(@AuthenticationPrincipal CurrentUser student) {
        return GlobalResponseDTO.success(scholarshipService.getMyScholarships(student));
    }
}

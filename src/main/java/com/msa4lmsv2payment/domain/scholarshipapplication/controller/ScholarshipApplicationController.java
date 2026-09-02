package com.msa4lmsv2payment.domain.scholarshipapplication.controller;

import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationCreateRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationPeriodCreateRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationReviewRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.response.ScholarshipApplicationPeriodResponseDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.response.ScholarshipApplicationResponseDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.service.ScholarshipApplicationService;
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

import java.util.List;

@Tag(name = "ScholarshipApplication", description = "학생 장학금 신청·심사·신청기간")
@RestController
@RequiredArgsConstructor
public class ScholarshipApplicationController {

    private final ScholarshipApplicationService scholarshipApplicationService;

    @Operation(summary = "장학금 신청", description = "STUDENT가 본인 등록금 고지에 대해 장학금을 신청한다. 해당 학기 신청기간 안에서만 가능하고, 심사 중인 신청이 있으면 거부된다.")
    @ApiResponse(responseCode = "201", description = "신청 성공")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/scholarship-applications")
    public GlobalResponseDTO<ScholarshipApplicationResponseDTO> createApplication(
            @AuthenticationPrincipal CurrentUser student,
            @RequestBody @Valid ScholarshipApplicationCreateRequestDTO request
    ) {
        return GlobalResponseDTO.success(scholarshipApplicationService.createApplication(student, request));
    }

    @Operation(summary = "내 장학금 신청 내역", description = "STUDENT 본인이 신청한 장학금 목록을 최신순으로 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payment/me/scholarship-applications")
    public GlobalResponseDTO<List<ScholarshipApplicationResponseDTO>> getMyApplications(@AuthenticationPrincipal CurrentUser student) {
        return GlobalResponseDTO.success(scholarshipApplicationService.getMyApplications(student));
    }

    @Operation(summary = "장학금 신청 심사", description = "ADMIN이 장학금 신청을 승인·반려한다. 승인 시 기존 장학금 감면 적용 로직을 그대로 재사용해 고지 금액 초과를 방어한다.")
    @ApiResponse(responseCode = "200", description = "심사 완료")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/payment/scholarship-applications/{applicationId}/review")
    public GlobalResponseDTO<ScholarshipApplicationResponseDTO> reviewApplication(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long applicationId,
            @RequestBody @Valid ScholarshipApplicationReviewRequestDTO request
    ) {
        return GlobalResponseDTO.success(scholarshipApplicationService.reviewApplication(admin, applicationId, request));
    }

    @Operation(summary = "장학금 신청기간 설정", description = "ADMIN이 학기별 장학금 신청기간을 설정한다. 학사일정 공지와 연결하려면 academicScheduleId를 함께 지정한다(선택).")
    @ApiResponse(responseCode = "201", description = "설정 성공")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/scholarship-application-periods")
    public GlobalResponseDTO<ScholarshipApplicationPeriodResponseDTO> createApplicationPeriod(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid ScholarshipApplicationPeriodCreateRequestDTO request
    ) {
        return GlobalResponseDTO.success(scholarshipApplicationService.createApplicationPeriod(admin, request));
    }

    @Operation(summary = "장학금 신청기간 조회", description = "학기별 장학금 신청기간과 오늘 기준 신청 가능 여부를 조회한다. 학생 화면에서 배너/신청 버튼 노출 여부를 판단하는 용도. STUDENT/ADMIN 모두 접근 가능.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CustomApiResponse({CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payment/scholarship-application-periods")
    public GlobalResponseDTO<ScholarshipApplicationPeriodResponseDTO> getApplicationPeriod(@RequestParam Long semesterId) {
        return GlobalResponseDTO.success(scholarshipApplicationService.getApplicationPeriod(semesterId));
    }
}

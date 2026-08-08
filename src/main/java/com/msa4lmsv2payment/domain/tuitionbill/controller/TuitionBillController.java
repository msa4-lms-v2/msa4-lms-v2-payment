package com.msa4lmsv2payment.domain.tuitionbill.controller;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.request.TuitionBillCreateRequestDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionPaymentStatusResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.response.PageRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payment", description = "등록금 고지·조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class TuitionBillController {

    private final TuitionBillService tuitionBillService;

    @Operation(summary = "관리자 등록금 고지", description = "ADMIN이 학생 개인에게 등록금을 고지한다. ADMIN 관리 범위·감사 로그 대상.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "고지 생성 성공"),
            @ApiResponse(responseCode = "403", description = "ADMIN 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 학번·학기")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payments/tuition-bills")
    public GlobalRes<TuitionBillResponseDTO> createTuitionBill(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid TuitionBillCreateRequestDTO request
    ) {
        return GlobalRes.success(tuitionBillService.createTuitionBill(admin, request));
    }

    @Operation(summary = "학생 등록금 고지서 조회", description = "STUDENT 본인의 등록금 고지서 단건을 조회한다. STUDENT 본인 데이터만 접근 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payments/student-tuition-bills")
    public GlobalRes<TuitionBillResponseDTO> getStudentTuitionBill(
            @AuthenticationPrincipal CurrentUser student,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(tuitionBillService.getStudentTuitionBill(student, tuitionBillId));
    }

    @Operation(summary = "관리자 등록금 목록 조회", description = "ADMIN이 상태별·페이지네이션으로 전체 등록금 고지 목록을 조회한다. ADMIN 관리 범위.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/payments/admin-tuition-bills")
    public GlobalRes<PageRes<TuitionBillResponseDTO>> getAdminTuitionBills(
            @RequestParam(required = false) TuitionBillStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return GlobalRes.success(tuitionBillService.getAdminTuitionBills(status, page, size));
    }

    @Operation(summary = "학생별 등록금 조회", description = "STUDENT 본인의 등록금 고지 목록을 조회한다. STUDENT 본인 데이터만 접근 가능.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payments/student-tuition")
    public GlobalRes<List<TuitionBillResponseDTO>> getStudentTuition(@AuthenticationPrincipal CurrentUser currentUser) {
        return GlobalRes.success(tuitionBillService.getMyTuitionBills(currentUser));
    }

    @Operation(summary = "등록금 납부 상태 조회", description = "등록금 고지 하나의 납부 상태를 조회한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/tuition-payment-status")
    public GlobalRes<TuitionPaymentStatusResponseDTO> getTuitionPaymentStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(tuitionBillService.getTuitionPaymentStatus(currentUser, tuitionBillId));
    }
}

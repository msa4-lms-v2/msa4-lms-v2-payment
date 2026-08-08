package com.msa4lmsv2payment.domain.tuitionbill.controller;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.request.TuitionBillCreateRequestDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionPaymentStatusResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.response.PageRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
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

@RestController
@RequiredArgsConstructor
public class TuitionBillController {

    private final TuitionBillService tuitionBillService;

    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payments/tuition-bills")
    public GlobalRes<TuitionBillResponseDTO> createTuitionBill(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestBody @Valid TuitionBillCreateRequestDTO request
    ) {
        return GlobalRes.success(tuitionBillService.createTuitionBill(admin, request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payments/student-tuition-bills")
    public GlobalRes<TuitionBillResponseDTO> getStudentTuitionBill(
            @AuthenticationPrincipal CurrentUser student,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(tuitionBillService.getStudentTuitionBill(student, tuitionBillId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/payments/admin-tuition-bills")
    public GlobalRes<PageRes<TuitionBillResponseDTO>> getAdminTuitionBills(
            @RequestParam(required = false) TuitionBillStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return GlobalRes.success(tuitionBillService.getAdminTuitionBills(status, page, size));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/api/payments/student-tuition")
    public GlobalRes<List<TuitionBillResponseDTO>> getStudentTuition(@AuthenticationPrincipal CurrentUser currentUser) {
        return GlobalRes.success(tuitionBillService.getMyTuitionBills(currentUser));
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/tuition-payment-status")
    public GlobalRes<TuitionPaymentStatusResponseDTO> getTuitionPaymentStatus(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam Long tuitionBillId
    ) {
        return GlobalRes.success(tuitionBillService.getTuitionPaymentStatus(currentUser, tuitionBillId));
    }
}

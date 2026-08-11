package com.msa4lmsv2payment.domain.paymenthealth.controller;

import com.msa4lmsv2payment.domain.paymenthealth.response.HealthCheckResponseDTO;
import com.msa4lmsv2payment.domain.paymenthealth.service.PaymentHealthService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "PG·가상계좌 외부 연결 확인")
@RestController
@RequiredArgsConstructor
public class PaymentHealthController {

    private final PaymentHealthService paymentHealthService;

    @Operation(summary = "PG Sandbox 연결 확인", description = "토스페이먼츠 PG 연결 상태(UP/DOWN)를 조회한다. 연결 실패도 200 정상 응답이다.")
    @ApiResponse(responseCode = "200", description = "조회 성공(UP 또는 DOWN)")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/pg-sandbox-health")
    public GlobalRes<HealthCheckResponseDTO> getPgSandboxHealth() {
        return GlobalRes.success(paymentHealthService.checkPgSandboxHealth());
    }

    @Operation(summary = "가상계좌 API 연결 확인", description = "토스페이먼츠 가상계좌 API 연결 상태(UP/DOWN)를 조회한다. 연결 실패도 200 정상 응답이다.")
    @ApiResponse(responseCode = "200", description = "조회 성공(UP 또는 DOWN)")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/virtual-account-health")
    public GlobalRes<HealthCheckResponseDTO> getVirtualAccountHealth() {
        return GlobalRes.success(paymentHealthService.checkVirtualAccountHealth());
    }
}

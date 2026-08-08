package com.msa4lmsv2payment.domain.paymenthealth.controller;

import com.msa4lmsv2payment.domain.paymenthealth.response.HealthCheckResponseDTO;
import com.msa4lmsv2payment.domain.paymenthealth.service.PaymentHealthService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentHealthController {

    private final PaymentHealthService paymentHealthService;

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/pg-sandbox-health")
    public GlobalRes<HealthCheckResponseDTO> getPgSandboxHealth() {
        return GlobalRes.success(paymentHealthService.checkPgSandboxHealth());
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @GetMapping("/api/payments/virtual-account-health")
    public GlobalRes<HealthCheckResponseDTO> getVirtualAccountHealth() {
        return GlobalRes.success(paymentHealthService.checkVirtualAccountHealth());
    }
}

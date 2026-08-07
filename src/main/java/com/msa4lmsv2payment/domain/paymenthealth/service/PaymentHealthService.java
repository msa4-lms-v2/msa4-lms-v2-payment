package com.msa4lmsv2payment.domain.paymenthealth.service;

import com.msa4lmsv2payment.domain.paymenthealth.response.HealthCheckResponseDTO;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentHealthService {

    private final TossPaymentsClient tossPaymentsClient;

    public HealthCheckResponseDTO checkPgSandboxHealth() {
        return check("PG_SANDBOX", "PG Sandbox");
    }

    public HealthCheckResponseDTO checkVirtualAccountHealth() {
        return check("VIRTUAL_ACCOUNT", "가상계좌 API");
    }

    private HealthCheckResponseDTO check(String feature, String label) {
        try {
            boolean connected = tossPaymentsClient.checkConnectivity();
            return connected
                    ? HealthCheckResponseDTO.up(feature, label + " 연결이 정상입니다.")
                    : HealthCheckResponseDTO.down(feature, label + " 연결에 실패했습니다.");
        } catch (IllegalStateException e) {
            return HealthCheckResponseDTO.down(feature, e.getMessage());
        }
    }
}

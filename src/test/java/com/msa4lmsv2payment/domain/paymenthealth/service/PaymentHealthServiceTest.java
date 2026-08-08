package com.msa4lmsv2payment.domain.paymenthealth.service;

import com.msa4lmsv2payment.domain.paymenthealth.response.HealthCheckResponseDTO;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHealthServiceTest {

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @InjectMocks
    private PaymentHealthService paymentHealthService;

    // SCRUM-79: PG Sandbox 연결 확인
    @Test
    void 연결에_성공하면_UP을_반환한다() {
        when(tossPaymentsClient.checkConnectivity()).thenReturn(true);

        HealthCheckResponseDTO result = paymentHealthService.checkPgSandboxHealth();

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.feature()).isEqualTo("PG_SANDBOX");
    }

    @Test
    void 연결에_실패하면_DOWN을_반환한다() {
        when(tossPaymentsClient.checkConnectivity()).thenReturn(false);

        HealthCheckResponseDTO result = paymentHealthService.checkPgSandboxHealth();

        assertThat(result.status()).isEqualTo("DOWN");
    }

    @Test
    void 시크릿키_미설정_예외는_예외_없이_DOWN으로_변환된다() {
        when(tossPaymentsClient.checkConnectivity())
                .thenThrow(new IllegalStateException("TOSS_SECRET_KEY가 설정되지 않았습니다."));

        HealthCheckResponseDTO result = paymentHealthService.checkPgSandboxHealth();

        assertThat(result.status()).isEqualTo("DOWN");
        assertThat(result.message()).contains("TOSS_SECRET_KEY");
    }

    // SCRUM-42: 가상계좌 API 연결 확인
    @Test
    void 가상계좌_연결확인은_별도_feature_이름을_가진다() {
        when(tossPaymentsClient.checkConnectivity()).thenReturn(true);

        HealthCheckResponseDTO result = paymentHealthService.checkVirtualAccountHealth();

        assertThat(result.feature()).isEqualTo("VIRTUAL_ACCOUNT");
        assertThat(result.status()).isEqualTo("UP");
    }
}

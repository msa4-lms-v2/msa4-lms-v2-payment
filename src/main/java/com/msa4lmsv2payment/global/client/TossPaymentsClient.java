package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.config.TossPaymentsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class TossPaymentsClient {

    private final RestClient restClient;
    private final boolean secretKeyConfigured;

    public TossPaymentsClient(TossPaymentsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout());
        requestFactory.setReadTimeout((int) properties.readTimeout());

        this.secretKeyConfigured = properties.secretKey() != null && !properties.secretKey().isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (secretKeyConfigured) {
                        headers.setBasicAuth(properties.secretKey(), "");
                    }
                })
                .build();
    }

    /**
     * 토스페이먼츠 테스트 상점 키로 실제 결제를 만들지 않고 연결·인증만 확인한다.
     * 존재하지 않는 결제키를 조회해 404(NOT_FOUND_PAYMENT)가 오면 인증·연결 모두 정상, 401/403이면 키 문제로 본다.
     */
    public boolean checkConnectivity() {
        if (!secretKeyConfigured) {
            throw new IllegalStateException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        }

        try {
            restClient.get()
                    .uri("/v1/payments/{paymentKey}", "msa4-lms-v2-health-check")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 401 || res.getStatusCode().value() == 403) {
                            throw new IllegalStateException("토스페이먼츠 인증에 실패했습니다. 시크릿 키를 확인하세요.");
                        }
                    })
                    .toBodilessEntity();
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 연결 확인 실패: {}", e.getMessage());
            return false;
        }
    }
}

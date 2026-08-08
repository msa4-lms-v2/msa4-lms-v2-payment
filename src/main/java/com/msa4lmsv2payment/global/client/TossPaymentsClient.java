package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.config.TossPaymentsProperties;
import com.msa4lmsv2payment.global.error.TossServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

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

    /**
     * 토스페이먼츠 가상계좌 발급 API(POST /v1/virtual-accounts) - MY-PLAN_payment.md 7-4절.
     * 입금 Webhook 없이 계좌 발급 자체만 완결되는 API다.
     */
    public TossVirtualAccountIssueResponse issueVirtualAccount(String orderId, String orderName, BigDecimal amount,
                                                                 String customerName, String bankCode) {
        if (!secretKeyConfigured) {
            throw new TossServiceUnavailableException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        }

        try {
            TossVirtualAccountIssueResponse response = restClient.post()
                    .uri("/v1/virtual-accounts")
                    .body(Map.of(
                            "orderId", orderId,
                            "orderName", orderName,
                            "amount", amount,
                            "customerName", customerName,
                            "bank", bankCode
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new TossServiceUnavailableException("토스페이먼츠 가상계좌 발급 요청이 거부됐습니다(상태 " + res.getStatusCode().value() + ").");
                    })
                    .body(TossVirtualAccountIssueResponse.class);

            if (response == null) {
                throw new TossServiceUnavailableException("토스페이먼츠 가상계좌 발급 응답이 올바르지 않습니다.");
            }
            return response;
        } catch (TossServiceUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 가상계좌 발급 실패: {}", e.getMessage());
            throw new TossServiceUnavailableException("토스페이먼츠 가상계좌 발급에 실패했습니다.");
        }
    }

    /**
     * 결제 승인(POST /v1/payments/confirm) - MY-PLAN_payment.md 10-2절, SCRUM-115.
     * 4xx는 예외로 던지지 않고 응답 그대로 반환한다 - "승인 거부"도 이 API의 정상적인 결과이지 시스템 오류가 아니므로,
     * 호출부(PaymentService)가 status를 보고 SUCCEEDED/FAILED를 판단한다.
     */
    public TossPaymentResponse confirmPayment(String paymentKey, String orderId, BigDecimal amount) {
        if (!secretKeyConfigured) {
            throw new TossServiceUnavailableException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        }

        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 4xx도 몸체에 실패 사유가 담긴 정상 응답이라 예외로 바꾸지 않고 그대로 읽는다.
                    })
                    .body(TossPaymentResponse.class);
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 결제 승인 호출 실패: {}", e.getMessage());
            throw new TossServiceUnavailableException("토스페이먼츠 결제 승인에 실패했습니다.");
        }
    }

    /**
     * 결제 단건 조회(GET /v1/payments/{paymentKey}) - SCRUM-110, confirm 호출이 타임아웃됐을 때
     * 실제로는 처리됐는지 재확인하는 복구용.
     */
    public TossPaymentResponse getPayment(String paymentKey) {
        if (!secretKeyConfigured) {
            throw new TossServiceUnavailableException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        }

        try {
            TossPaymentResponse response = restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new TossServiceUnavailableException("존재하지 않는 결제입니다: " + paymentKey);
                    })
                    .body(TossPaymentResponse.class);

            if (response == null) {
                throw new TossServiceUnavailableException("토스페이먼츠 결제 조회 응답이 올바르지 않습니다.");
            }
            return response;
        } catch (TossServiceUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 결제 조회 실패: {}", e.getMessage());
            throw new TossServiceUnavailableException("토스페이먼츠 결제 조회에 실패했습니다.");
        }
    }
}

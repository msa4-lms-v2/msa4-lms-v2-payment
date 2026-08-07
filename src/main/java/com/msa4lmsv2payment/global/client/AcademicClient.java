package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.error.AcademicServiceUnavailableException;
import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AcademicClient {

    private static final String SERVICE_NAME = "msa4-lms-v2-payment";
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;

    public AcademicClient(RestClient.Builder internalApiRestClientBuilder,
                           @Value("${academic.service.base-url}") String baseUrl,
                           @Value("${academic.service.token}") String serviceToken) {
        this.restClient = internalApiRestClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Name", SERVICE_NAME)
                .defaultHeader("Authorization", "Bearer " + serviceToken)
                .build();
    }

    /**
     * docs-v2/MSA-LMS_INTEGRATION.md 7절 - JWT 사용자 ID로 본인의 학번(students.id)을 조회한다.
     */
    public AcademicStudentResponse findStudentByUserId(Long userId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                InternalApiResponse<AcademicStudentResponse> response = restClient.get()
                        .uri("/internal/academic/students/by-user/{userId}", userId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            throw new NotStudentAccountException("학생 계정이 아니거나 존재하지 않는 사용자입니다.");
                        })
                        .body(new ParameterizedTypeReference<InternalApiResponse<AcademicStudentResponse>>() {});

                if (response == null || !response.success()) {
                    throw new AcademicServiceUnavailableException("학적 서비스 응답이 올바르지 않습니다.");
                }
                return response.data();
            } catch (NotStudentAccountException e) {
                throw e;
            } catch (RestClientException e) {
                log.warn("Academic 서비스 호출 실패({}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    throw new AcademicServiceUnavailableException("학적 서비스에 연결할 수 없습니다.");
                }
                sleep(attempt == 1 ? 200 : 800);
            }
        }
        throw new AcademicServiceUnavailableException("학적 서비스에 연결할 수 없습니다.");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

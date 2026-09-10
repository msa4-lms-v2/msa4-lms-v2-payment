package com.msa4lmsv2payment.global.client;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Kafka 24시간 보관을 넘겨 스냅샷에 반영되지 못한 gap을 요청 시점에 보정하는 읽기 전용 백필.
 * Academic의 시스템 전용 /snapshot 엔드포인트를 호출한다 - 실패하거나 대상이 없으면 empty를 반환하고,
 * 호출부는 기존 not-found 처리를 그대로 따른다.
 */
@Slf4j
@Component
public class AcademicResyncClient {

    private static final String SERVICE_NAME = "msa4-lms-v2-payment";
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;

    public AcademicResyncClient(RestClient.Builder internalApiRestClientBuilder,
                                @Value("${gateway.internal.base-url}") String baseUrl) {
        this.restClient = internalApiRestClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Name", SERVICE_NAME)
                .build();
    }

    public Optional<StudentSnapshotSyncResponse> fetchStudent(Long studentId) {
        return get("/api/academic/students/{studentId}/snapshot", studentId,
                new ParameterizedTypeReference<>() {});
    }

    public Optional<StudentSnapshotSyncResponse> fetchStudentByUserId(Long userId) {
        return get("/api/academic/students/by-user/{userId}/snapshot", userId,
                new ParameterizedTypeReference<>() {});
    }

    public Optional<SemesterSnapshotSyncResponse> fetchSemester(Long semesterId) {
        return get("/api/academic/catalog/semesters/{semesterId}/snapshot", semesterId,
                new ParameterizedTypeReference<>() {});
    }

    public Optional<WithdrawalSnapshotSyncResponse> fetchWithdrawal(Long withdrawalId) {
        return get("/api/academic/withdrawals/{withdrawalId}/snapshot", withdrawalId,
                new ParameterizedTypeReference<>() {});
    }

    // 증명서 발급 자격(재학·졸업요건, 재직 상태)은 스냅샷 테이블로 캐시하지 않고 매번 실시간으로 조회한다 -
    // 발급 빈도가 낮고, 발급 시점의 최신 상태를 반영해야 하는 문서라 캐시 신선도 문제를 감수할 이유가 없다.
    public Optional<StudentCertificateEligibilityResponse> fetchStudentCertificateEligibility(Long studentId) {
        return get("/api/academic/students/{studentId}/certificate-snapshot", studentId,
                new ParameterizedTypeReference<>() {});
    }

    public Optional<ProfessorCertificateEligibilityResponse> fetchProfessorCertificateEligibility(Long professorId) {
        return get("/api/academic/professors/{professorId}/certificate-snapshot", professorId,
                new ParameterizedTypeReference<>() {});
    }

    public Optional<ProfessorCertificateEligibilityResponse> fetchProfessorCertificateEligibilityByUserId(Long userId) {
        return get("/api/academic/professors/by-user/{userId}/certificate-snapshot", userId,
                new ParameterizedTypeReference<>() {});
    }

    private <T> Optional<T> get(String uriTemplate, Long id, ParameterizedTypeReference<InternalApiResponse<T>> typeRef) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                InternalApiResponse<T> response = restClient.get()
                        .uri(uriTemplate, id)
                        .retrieve()
                        .onStatus(status -> status.value() == 404, (req, res) -> {
                        })
                        .body(typeRef);
                if (response == null || !response.isSuccess() || response.data() == null) {
                    return Optional.empty();
                }
                return Optional.of(response.data());
            } catch (RestClientException exception) {
                log.warn("Academic 재동기화 호출 실패({}/{}): {}", attempt, MAX_ATTEMPTS, exception.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    return Optional.empty();
                }
                sleep(attempt == 1 ? 200 : 800);
            }
        }
        return Optional.empty();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

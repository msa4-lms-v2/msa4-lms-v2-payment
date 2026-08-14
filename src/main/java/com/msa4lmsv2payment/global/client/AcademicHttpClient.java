package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.AcademicServiceUnavailableException;
import com.msa4lmsv2payment.global.error.BusinessException;
import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Profile("!stub")
@Component
public class AcademicHttpClient implements AcademicClient {

    private static final String SERVICE_NAME = "msa4-lms-v2-payment";
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;

    public AcademicHttpClient(RestClient.Builder internalApiRestClientBuilder,
                               @Value("${gateway.internal.base-url}") String baseUrl) {
        this.restClient = internalApiRestClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Name", SERVICE_NAME)
                .build();
    }

    @Override
    public AcademicStudentResponse findStudentByUserId(Long userId) {
        return get(spec -> spec.uri("/api/academic/students/by-user/{userId}", userId),
                new ParameterizedTypeReference<InternalApiResponse<AcademicStudentResponse>>() {},
                () -> new NotStudentAccountException("학생 계정이 아니거나 존재하지 않는 사용자입니다."));
    }

    @Override
    public AcademicStudentExistsResponse findStudent(Long studentId) {
        return get(spec -> spec.uri("/api/academic/students/{studentId}", studentId),
                new ParameterizedTypeReference<InternalApiResponse<AcademicStudentExistsResponse>>() {},
                () -> new AcademicResourceNotFoundException("존재하지 않는 학번입니다: " + studentId));
    }

    @Override
    public AcademicSemesterResponse findSemester(Long semesterId) {
        return get(spec -> spec.uri("/api/academic/semesters/{semesterId}", semesterId),
                new ParameterizedTypeReference<InternalApiResponse<AcademicSemesterResponse>>() {},
                () -> new AcademicResourceNotFoundException("존재하지 않는 학기입니다: " + semesterId));
    }

    @Override
    public AcademicWithdrawalResponse findWithdrawal(Long withdrawalId) {
        return get(spec -> spec.uri("/api/academic/withdrawals/{withdrawalId}", withdrawalId),
                new ParameterizedTypeReference<InternalApiResponse<AcademicWithdrawalResponse>>() {},
                () -> new AcademicResourceNotFoundException("자퇴 신청을 찾을 수 없습니다: withdrawalId=" + withdrawalId));
    }

    private <T> T get(Function<RestClient.RequestHeadersUriSpec<?>, RestClient.RequestHeadersSpec<?>> uriCustomizer,
                       ParameterizedTypeReference<InternalApiResponse<T>> typeRef,
                       Supplier<RuntimeException> notFoundExceptionSupplier) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                InternalApiResponse<T> response = uriCustomizer.apply(restClient.get())
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            if (res.getStatusCode().value() == 404) {
                                throw notFoundExceptionSupplier.get();
                            }
                            throw new AcademicServiceUnavailableException(
                                    "Academic API 호출 권한이 없습니다(상태 " + res.getStatusCode().value() + ").");
                        })
                        .body(typeRef);

                if (response == null || !response.isSuccess()) {
                    throw new AcademicServiceUnavailableException("학적 서비스 응답이 올바르지 않습니다.");
                }
                return response.data();
            } catch (BusinessException e) {
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

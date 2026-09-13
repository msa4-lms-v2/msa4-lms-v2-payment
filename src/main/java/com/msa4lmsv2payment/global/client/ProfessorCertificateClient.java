package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.security.CurrentUser;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.CertificateNotEligibleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Gateway가 검증한 주체만 내부 Academic에 전달한다. 외부 요청의 식별자를 입력으로 받지 않는다.
// Academic은 기존 GatewayHeaderAuthenticationFilter와 PROFESSOR 권한 검사로 보호된다.
@Component
public class ProfessorCertificateClient {
    private final RestClient client;
    public ProfessorCertificateClient(RestClient.Builder internalApiRestClientBuilder,
            @Value("${academic.internal.base-url:http://localhost:8082}") String baseUrl) {
        this.client = internalApiRestClientBuilder.clone().baseUrl(baseUrl).build();
    }
    public ProfessorCareerResponse fetch(CurrentUser user) {
        if (!"PROFESSOR".equals(user.role())) throw new CertificateNotEligibleException("교수 본인만 발급할 수 있습니다.");
        ProfessorCareerResponse data;
        try {
            InternalApiResponse<ProfessorCareerResponse> response = client.get()
                    .uri("/api/academic/professors/me/certificate-career")
                    .header("X-User-Id", user.id().toString()).header("X-User-Role", "PROFESSOR")
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (response == null || !response.isSuccess() || response.data() == null)
                throw new AcademicResourceNotFoundException("교수 경력 정보를 확인할 수 없습니다.");
            data = response.data();
        } catch (RestClientException error) {
            throw new AcademicResourceNotFoundException("교수 경력 정보를 확인할 수 없습니다.");
        }
        if (!user.id().equals(data.userId()) || data.professor() == null || data.professor().professorId() == null)
            throw new CertificateNotEligibleException("교수 본인 정보를 확인할 수 없습니다.");
        return data;
    }
}

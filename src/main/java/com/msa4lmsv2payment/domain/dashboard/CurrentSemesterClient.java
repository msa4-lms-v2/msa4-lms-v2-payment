package com.msa4lmsv2payment.domain.dashboard;

import com.msa4lmsv2payment.global.client.InternalApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static com.msa4lmsv2payment.domain.dashboard.AdminDashboardResponse.CurrentSemester;

@Component
public class CurrentSemesterClient {
    private final RestClient client;

    public CurrentSemesterClient(RestClient.Builder internalApiRestClientBuilder,
                                 @Value("${gateway.internal.base-url}") String baseUrl) {
        client = internalApiRestClientBuilder.clone().baseUrl(baseUrl)
                .defaultHeader("X-Service-Name", "msa4-lms-v2-payment").build();
    }

    public CurrentSemester getCurrentSemester() {
        var response = client.get().uri("/api/academic/catalog/semesters/current/snapshot")
                .retrieve().body(new ParameterizedTypeReference<InternalApiResponse<CurrentSemester>>() {});
        if (response == null || !response.isSuccess()) {
            throw new RestClientException("현재 학기 조회 응답을 확인할 수 없습니다.");
        }
        var semester = response.data();
        if (semester != null && (semester.id() == null || semester.id() <= 0 || semester.year() <= 0
                || semester.label() == null || semester.label().isBlank())) {
            throw new RestClientException("현재 학기 조회 응답이 올바르지 않습니다.");
        }
        return semester;
    }
}

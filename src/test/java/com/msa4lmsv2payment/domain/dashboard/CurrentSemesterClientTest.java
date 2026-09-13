package com.msa4lmsv2payment.domain.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CurrentSemesterClientTest {
    MockRestServiceServer server;
    CurrentSemesterClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CurrentSemesterClient(builder, "http://academic.internal");
    }
    @Test void readsConfiguredSemesterWithoutGuessingDates() {
        server.expect(requestTo("http://academic.internal/api/academic/catalog/semesters/current/snapshot"))
                .andExpect(header("X-Service-Name", "msa4-lms-v2-payment"))
                .andRespond(withSuccess("{\"code\":\"00\",\"data\":{\"id\":20,\"year\":2026,\"label\":\"2학기\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.getCurrentSemester()).isEqualTo(new AdminDashboardResponse.CurrentSemester(20L,2026,"2학기"));
        server.verify();
    }
    @Test void successfulNullMeansNoCurrentSemester() {
        server.expect(requestTo("http://academic.internal/api/academic/catalog/semesters/current/snapshot"))
                .andRespond(withSuccess("{\"code\":\"00\",\"data\":null}", MediaType.APPLICATION_JSON));
        assertThat(client.getCurrentSemester()).isNull();
    }
    @Test void outageIsNotSilentlyReportedAsNoSemester() {
        server.expect(requestTo("http://academic.internal/api/academic/catalog/semesters/current/snapshot"))
                .andRespond(withServerError());
        assertThatThrownBy(client::getCurrentSemester).isInstanceOf(RestClientException.class);
    }
}

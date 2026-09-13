package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.security.CurrentUser;
import com.msa4lmsv2payment.global.error.CertificateNotEligibleException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfessorCertificateClientTest {
    @Test void forwardsVerifiedPrincipalAndRejectsWrongOwner() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new ProfessorCertificateClient(builder, "http://academic.test");
        server.expect(requestTo("http://academic.test/api/academic/professors/me/certificate-career"))
            .andExpect(header("X-User-Id", "7")).andExpect(header("X-User-Role", "PROFESSOR"))
            .andRespond(withSuccess("""
              {"code":"00","data":{"userId":99,"professor":{"professorId":8},"lectures":[]}}
              """, MediaType.APPLICATION_JSON));
        assertThrows(CertificateNotEligibleException.class, () -> client.fetch(new CurrentUser(7L,"PROFESSOR")));
        server.verify();
    }
    @Test void studentCannotFetchProfessorCareer() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var client=new ProfessorCertificateClient(builder,"http://academic.test");
        assertThrows(CertificateNotEligibleException.class,()->client.fetch(new CurrentUser(7L,"STUDENT")));
        server.verify();
    }
}

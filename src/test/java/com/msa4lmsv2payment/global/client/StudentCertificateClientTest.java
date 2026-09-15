package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentCertificateClientTest {
    @Test void forwardsStudentPrincipalAndRejectsMismatchedStudent() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AcademicResyncClient(builder, "http://academic.test");
        server.expect(requestTo("http://academic.test/api/academic/students/8/certificate-snapshot"))
                .andExpect(header("X-User-Id", "7")).andExpect(header("X-User-Role", "STUDENT"))
                .andRespond(withSuccess("""
                        {"code":"00","data":{"studentId":99,"gradeLevel":4,"admissionYear":2022,"academicStatus":"ENROLLED"}}
                        """, MediaType.APPLICATION_JSON));
        assertTrue(client.fetchStudentCertificateEligibility(8L, new CurrentUser(7L, "STUDENT")).isEmpty());
        server.verify();
    }

    @Test void verifiedStudentResponseIsAccepted() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AcademicResyncClient(builder, "http://academic.test");
        server.expect(requestTo("http://academic.test/api/academic/students/8/certificate-snapshot"))
                .andExpect(header("X-User-Id", "7")).andExpect(header("X-User-Role", "STUDENT"))
                .andRespond(withSuccess("""
                        {"code":"00","data":{"studentId":8,"gradeLevel":4,"admissionYear":2022,"academicStatus":"GRADUATED","graduationSatisfied":true}}
                        """, MediaType.APPLICATION_JSON));
        assertTrue(client.fetchStudentCertificateEligibility(8L, new CurrentUser(7L, "STUDENT")).orElseThrow().graduationSatisfied());
        server.verify();
    }

    @Test void nonStudentCannotFetchStudentCertificate() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AcademicResyncClient(builder, "http://academic.test");
        assertTrue(client.fetchStudentCertificateEligibility(8L, new CurrentUser(7L, "PROFESSOR")).isEmpty());
        server.verify();
    }
}

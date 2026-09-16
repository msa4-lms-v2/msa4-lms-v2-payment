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
    @Test void transcriptUsesAuthenticatedStudentAndFullGradeQuery() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AcademicResyncClient(builder, "http://academic.test");
        server.expect(requestTo("http://academic.test/api/academic/grades/me?sortBy=ACADEMIC_YEAR&direction=ASC"))
                .andExpect(header("X-User-Id", "7")).andExpect(header("X-User-Role", "STUDENT"))
                .andRespond(withSuccess("""
                        {"code":"00","data":{"totalGpa":3.75,"totalCredits":80,"queryGpa":3.75,"queryCredits":3,
                        "grades":[{"enrollmentId":1,"classId":2,"academicYear":2025,"term":"FIRST",
                        "courseCode":"CS101","courseName":"자료구조","credits":3,"totalScore":90,
                        "letterGrade":"A","gradePoint":4.0,"reflectedInGpa":true}]}}
                        """, MediaType.APPLICATION_JSON));
        var result = client.fetchStudentGrades(new CurrentUser(7L, "STUDENT")).orElseThrow();
        assertEquals(3, result.queryCredits());
        assertEquals("자료구조", result.grades().getFirst().courseName());
        assertTrue(result.grades().getFirst().reflectedInGpa());
        assertTrue(client.fetchStudentGrades(new CurrentUser(7L, "PROFESSOR")).isEmpty());
        server.verify();
    }

    @Test void gradeServerFailureDoesNotBecomeAnEmptyTranscript() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AcademicResyncClient(builder, "http://academic.test");
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                requestTo("http://academic.test/api/academic/grades/me?sortBy=ACADEMIC_YEAR&direction=ASC"))
                .andRespond(withServerError());
        assertTrue(client.fetchStudentGrades(new CurrentUser(7L, "STUDENT")).isEmpty());
        server.verify();
    }
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

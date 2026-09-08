package com.msa4lmsv2payment.domain.document;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-67/172 - 증명서 진위확인은 인증 헤더 없이(공개 API) 호출 가능해야 하고, 학생·교수 식별 정보를 응답에 담지 않아야 한다.
 * SCRUM-171 - 증명서 폐기는 ADMIN 전용이어야 한다.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificateSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void 진위확인은_인증_헤더_없이_호출할_수_있고_개인정보를_담지_않는다() throws Exception {
        Document document = documentRepository.save(
                new Document(80L, null, DocumentType.PAYMENT_CERTIFICATE, "token-public-1", "hash-1"));

        mockMvc.perform(get("/api/payment/certificates/verify").param("token", "token-public-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("VALID"))
                .andExpect(jsonPath("$.data.documentType").value("PAYMENT_CERTIFICATE"));
    }

    @Test
    void 존재하지_않는_토큰은_404다() throws Exception {
        mockMvc.perform(get("/api/payment/certificates/verify").param("token", "no-such-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 폐기는_인증_헤더가_없으면_401이다() throws Exception {
        Document document = documentRepository.save(
                new Document(81L, null, DocumentType.PAYMENT_CERTIFICATE, "token-revoke-1", "hash-2"));

        mockMvc.perform(patch("/api/payment/certificates/" + document.getId() + "/revoke")
                        .contentType("application/json")
                        .content("""
                                {"reason":"오발급"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void STUDENT는_폐기할_수_없다() throws Exception {
        Document document = documentRepository.save(
                new Document(82L, null, DocumentType.PAYMENT_CERTIFICATE, "token-revoke-2", "hash-3"));

        mockMvc.perform(patch("/api/payment/certificates/" + document.getId() + "/revoke")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT")
                        .contentType("application/json")
                        .content("""
                                {"reason":"오발급"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN이_폐기하면_이후_진위확인은_REVOKED다() throws Exception {
        Document document = documentRepository.save(
                new Document(83L, null, DocumentType.PAYMENT_CERTIFICATE, "token-revoke-3", "hash-4"));

        mockMvc.perform(patch("/api/payment/certificates/" + document.getId() + "/revoke")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "2")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                        .contentType("application/json")
                        .content("""
                                {"reason":"오발급"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/payment/certificates/verify").param("token", "token-revoke-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("REVOKED"));
    }
}

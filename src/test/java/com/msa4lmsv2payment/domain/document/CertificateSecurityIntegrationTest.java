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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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

    // SCRUM-183 - QR에 담긴 서명(qrHash)이 토큰의 실제 SHA-256 해시와 다르면(위조된 QR·수동 조작 시도)
    // HTTP 계층까지 관통해 거부되는지 확인한다. 기존 테스트들은 qrHash 파라미터 자체를 넘긴 적이 없어
    // DocumentController -> DocumentService의 서명 검증 경로가 실제로는 검증되지 않고 있었다.
    @Test
    void qrHash가_토큰의_실제_해시와_일치하면_VALID를_반환한다() throws Exception {
        String token = "token-sig-match-1";
        String realHash = sha256Hex(token);
        documentRepository.save(new Document(84L, null, DocumentType.PAYMENT_CERTIFICATE, token, realHash));

        mockMvc.perform(get("/api/payment/certificates/verify").param("token", token).param("qrHash", realHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("VALID"));
    }

    @Test
    void qrHash가_변조되면_서명불일치로_400을_반환한다() throws Exception {
        String token = "token-sig-tampered-1";
        String realHash = sha256Hex(token);
        documentRepository.save(new Document(85L, null, DocumentType.PAYMENT_CERTIFICATE, token, realHash));

        mockMvc.perform(get("/api/payment/certificates/verify")
                        .param("token", token)
                        .param("qrHash", "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E21"));
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

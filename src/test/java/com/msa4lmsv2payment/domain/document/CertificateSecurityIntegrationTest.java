package com.msa4lmsv2payment.domain.document;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerificationResult;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.repository.DocumentVerificationRepository;
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
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private DocumentVerificationRepository documentVerificationRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService tuitionBillService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.msa4lmsv2payment.global.file.FileStorageService fileStorageService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.msa4lmsv2payment.global.client.AcademicResyncClient academicResyncClient;

    @Test
    void 성적증명서_POST부터_한글PDF와_본인내역_다운로드까지_연결된다() throws Exception {
        var user = new com.msa4lmsv2payment.global.security.CurrentUser(7L, "STUDENT");
        org.mockito.Mockito.when(tuitionBillService.resolveStudentId(user)).thenReturn(9201L);
        org.mockito.Mockito.when(academicResyncClient.fetchStudentCertificateEligibility(9201L, user))
                .thenReturn(java.util.Optional.of(new com.msa4lmsv2payment.global.client.StudentCertificateEligibilityResponse(
                        9201L, "22019999", "성적검증", "컴퓨터학과", "공과대학", (byte)4, (short)2022, "ENROLLED", false, 3)));
        var grades = new java.util.ArrayList<com.msa4lmsv2payment.global.client.StudentGradeResponse.Grade>();
        for (int i = 0; i < 30; i++) grades.add(new com.msa4lmsv2payment.global.client.StudentGradeResponse.Grade(
                (short)2025, "FIRST", "CS" + i, "교과목" + i, (byte)3, "A+", new java.math.BigDecimal("4.5"), true));
        org.mockito.Mockito.when(academicResyncClient.fetchStudentGrades(user)).thenReturn(java.util.Optional.of(
                new com.msa4lmsv2payment.global.client.StudentGradeResponse(new java.math.BigDecimal("4.50"), 90, grades)));
        var pdf = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        org.mockito.Mockito.when(fileStorageService.upload(org.mockito.ArgumentMatchers.eq("certificates/grade"),
                org.mockito.ArgumentMatchers.eq(".pdf"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("application/pdf")))
                .thenAnswer(inv -> { pdf.set(inv.getArgument(2)); return "grade-fixture.pdf"; });
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/certificates")
                .header("X-User-Id", "7").header("X-User-Role", "STUDENT")
                .contentType("application/json").content("{\"documentType\":\"GRADE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.documentType").value("GRADE"));
        try (var document = org.apache.pdfbox.pdmodel.PDDocument.load(pdf.get())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                    .contains("성 적 증 명 서", "22019999", "교과목0", "교과목29", "90학점 / 4.50");
        }
        var saved = documentRepository.findByStudentIdOrderByIssuedAtDescIdDesc(9201L,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(saved).hasSize(1);
        org.mockito.Mockito.when(fileStorageService.download("grade-fixture.pdf")).thenReturn(pdf.get());
        mockMvc.perform(get("/api/payment/certificates/" + saved.getFirst().getId() + "/content")
                .header("X-User-Id", "7").header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf.get()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/certificates")
                .header("X-User-Id", "7").header("X-User-Role", "PROFESSOR")
                .contentType("application/json").content("{\"documentType\":\"GRADE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학생_내역과_PDF는_로그인이_필요하고_교수는_학생_내역을_읽을_수_없다() throws Exception {
        mockMvc.perform(get("/api/payment/students/me/certificates")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payment/certificates/1/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payment/students/me/certificates")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "7")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "PROFESSOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 학생은_DB에_저장된_본인_내역과_PDF만_조회한다() throws Exception {
        var user = new com.msa4lmsv2payment.global.security.CurrentUser(7L, "STUDENT");
        org.mockito.Mockito.when(tuitionBillService.resolveStudentId(user)).thenReturn(9101L);
        var own = documentRepository.save(new Document(9101L, null, DocumentType.ENROLLMENT, "own-history", "hash", "fixture.pdf"));
        var other = documentRepository.save(new Document(9102L, null, DocumentType.ENROLLMENT, "other-history", "hash", "other.pdf"));
        byte[] pdf = "%PDF-local-fixture".getBytes(StandardCharsets.US_ASCII);
        org.mockito.Mockito.when(fileStorageService.download("fixture.pdf")).thenReturn(pdf);
        mockMvc.perform(get("/api/payment/students/me/certificates")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "7")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(own.getId()));
        mockMvc.perform(get("/api/payment/certificates/" + own.getId() + "/content")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "7")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/payment/certificates/" + other.getId() + "/content")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "7")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().is4xxClientError());
        org.mockito.Mockito.verify(fileStorageService, org.mockito.Mockito.never()).download("other.pdf");
    }

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
        String realHash = hmacSignHex(token);
        documentRepository.save(new Document(84L, null, DocumentType.PAYMENT_CERTIFICATE, token, realHash));

        mockMvc.perform(get("/api/payment/certificates/verify").param("token", token).param("qrHash", realHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("VALID"));
    }

    @Test
    void qrHash가_변조되면_서명불일치로_400을_반환하고_위변조_시도가_감사_기록에_남는다() throws Exception {
        String token = "token-sig-tampered-1";
        String realHash = hmacSignHex(token);
        Document document = documentRepository.save(new Document(85L, null, DocumentType.PAYMENT_CERTIFICATE, token, realHash));

        mockMvc.perform(get("/api/payment/certificates/verify")
                        .param("token", token)
                        .param("qrHash", "0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E21"));

        assertThat(documentVerificationRepository.findAll()).anySatisfy(verification -> {
            assertThat(verification.getDocumentId()).isEqualTo(document.getId());
            assertThat(verification.getResult()).isEqualTo(DocumentVerificationResult.SIGNATURE_MISMATCH);
        });
    }

    // DocumentService.hmacSign()과 동일한 알고리즘·키(application-test.yaml의 certificate.signing-key)로 계산한다.
    private String hmacSignHex(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-signing-key-for-ci".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}

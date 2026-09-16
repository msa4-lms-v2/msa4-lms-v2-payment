package com.msa4lmsv2payment.domain.document.service;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerificationResult;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.repository.DocumentVerificationRepository;
import com.msa4lmsv2payment.domain.document.request.DocumentRevokeRequestDTO;
import com.msa4lmsv2payment.domain.document.response.CertificateVerificationResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicResyncClient;
import com.msa4lmsv2payment.global.client.ProfessorCareerResponse;
import com.msa4lmsv2payment.global.client.ProfessorCertificateEligibilityResponse;
import com.msa4lmsv2payment.global.error.CertificateNotEligibleException;
import java.util.List;
import com.msa4lmsv2payment.global.document.CertificatePdfGenerator;
import com.msa4lmsv2payment.global.error.DocumentAlreadyRevokedException;
import com.msa4lmsv2payment.global.error.DocumentNotFoundException;
import com.msa4lmsv2payment.global.error.DocumentSignatureMismatchException;
import com.msa4lmsv2payment.global.file.FileStorageService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";
    private static final String SIGNING_KEY = "test-signing-key";

    @Mock DocumentRepository documentRepository;
    @Mock DocumentVerificationRepository documentVerificationRepository;
    @Mock TuitionBillService tuitionBillService;
    @Mock PaymentService paymentService;
    @Mock AuditLogRecorder auditLogRecorder;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;
    @Mock AcademicResyncClient academicResyncClient;
    @Mock FileStorageService fileStorageService;
    @Mock CertificatePdfGenerator pdfGenerator;
    @Mock com.msa4lmsv2payment.global.client.ProfessorCertificateClient professorCertificateClient;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new DocumentService(documentRepository, documentVerificationRepository,
                tuitionBillService, paymentService, auditLogRecorder, transactionManager,
                academicResyncClient, fileStorageService, pdfGenerator, professorCertificateClient,
                SIGNING_KEY, "http://localhost:5176");
    }

    // DocumentService.hmacSign()과 동일한 알고리즘·키로 계산해 테스트에서 재사용한다.
    @Test
    void 본인_PDF는_저장소에서_직접_읽고_재다운로드시_재발급하지_않는다() {
        var user = new CurrentUser(7L, "STUDENT");
        when(tuitionBillService.resolveStudentId(user)).thenReturn(8L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(
                new Document(8L, null, DocumentType.ENROLLMENT, TOKEN, "hash", "test.pdf")));
        byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.download("test.pdf")).thenReturn(pdf);
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, service.downloadCertificate(user, 1L));
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, service.downloadCertificate(user, 1L));
        verify(documentRepository, never()).save(any());
        verify(fileStorageService, never()).presignedDownloadUrl(any());
    }

    @Test
    void 타인의_PDF와_폐기된_PDF는_저장소를_읽기_전에_거부한다() {
        var user = new CurrentUser(7L, "STUDENT");
        when(tuitionBillService.resolveStudentId(user)).thenReturn(8L);
        var other = new Document(99L, null, DocumentType.ENROLLMENT, TOKEN, "hash", "other.pdf");
        var revoked = new Document(8L, null, DocumentType.ENROLLMENT, TOKEN, "hash", "revoked.pdf");
        revoked.revoke();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(other));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(revoked));
        assertThrows(CertificateNotEligibleException.class, () -> service.downloadCertificate(user, 1L));
        assertThrows(CertificateNotEligibleException.class, () -> service.downloadCertificate(user, 2L));
        verify(fileStorageService, never()).download(any());
    }

    private String hmacSign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 존재하지_않는_토큰은_거부한다() {
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.verifyCertificate(TOKEN, null, "127.0.0.1"));
        verify(documentVerificationRepository, never()).save(any());
    }

    @Test
    void 유효한_토큰은_VALID를_반환하고_조회_이력을_남긴다() {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, "irrelevant");
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        CertificateVerificationResponseDTO response = service.verifyCertificate(TOKEN, null, "127.0.0.1");

        assertEquals(DocumentVerificationResult.VALID, response.result());
        verify(documentVerificationRepository).save(any());
    }

    @Test
    void 폐기된_증명서는_REVOKED를_반환한다() {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, "irrelevant");
        document.revoke();
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        CertificateVerificationResponseDTO response = service.verifyCertificate(TOKEN, null, "127.0.0.1");

        assertEquals(DocumentVerificationResult.REVOKED, response.result());
    }

    @Test
    void qrHash가_토큰_해시와_다르면_서명불일치로_거부하고_위변조_시도를_기록한다() throws Exception {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, hmacSign(TOKEN));
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        assertThrows(DocumentSignatureMismatchException.class,
                () -> service.verifyCertificate(TOKEN, "tampered-hash", "127.0.0.1"));
        verify(documentVerificationRepository).save(any());
    }

    @Test
    void qrHash가_토큰_해시와_일치하면_통과한다() throws Exception {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, hmacSign(TOKEN));
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        CertificateVerificationResponseDTO response = service.verifyCertificate(TOKEN, hmacSign(TOKEN), "127.0.0.1");

        assertEquals(DocumentVerificationResult.VALID, response.result());
    }

    @Test
    void 존재하지_않는_증명서_폐기는_거부한다() {
        when(documentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class,
                () -> service.revokeDocument(admin(), 1L, new DocumentRevokeRequestDTO("사유")));
    }

    @Test
    void 이미_폐기된_증명서를_다시_폐기하면_거부한다() {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, "hash");
        document.revoke();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(DocumentAlreadyRevokedException.class,
                () -> service.revokeDocument(admin(), 1L, new DocumentRevokeRequestDTO("사유")));
    }

    @Test
    void 정상_폐기_후_진위확인하면_REVOKED다() {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, "hash");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        service.revokeDocument(admin(), 1L, new DocumentRevokeRequestDTO("오발급 정정"));

        verify(auditLogRecorder).record(any(), any(), any(), any(), any(), any());
        assertTrue(document.isRevoked());
    }

    private ProfessorCareerResponse career(List<ProfessorCareerResponse.Teaching> lectures) {
        return new ProfessorCareerResponse(7L, new ProfessorCertificateEligibilityResponse(8L, "P-TEST", "테스트 교수",
                "학과", "대학", (short)2020, "ACTIVE"), lectures);
    }

    @Test
    void 강의_이력이_없으면_PDF와_문서를_만들지_않는다() {
        var user = new CurrentUser(7L, "PROFESSOR");
        when(professorCertificateClient.fetch(user)).thenReturn(career(List.of()));
        assertThrows(CertificateNotEligibleException.class, () -> service.issueCareerCertificate(user, true));
        verify(documentRepository, never()).save(any());
        verify(pdfGenerator, never()).generate(any(), any(), any(), any());
    }

    @Test
    void 경력증명서는_등록된_본인_교수번호로_저장한다() {
        var user = new CurrentUser(7L, "PROFESSOR");
        when(professorCertificateClient.fetch(user)).thenReturn(career(List.of()));
        when(pdfGenerator.generate(any(), any(), any(), any())).thenReturn(new byte[]{1});
        when(fileStorageService.upload(any(), any(), any(), any())).thenReturn("certificates/career/test.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = service.issueCareerCertificate(user, false);
        assertEquals(DocumentType.CAREER, result.documentType());
        var captor = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertEquals(8L, captor.getValue().getProfessorId());
        assertEquals(null, captor.getValue().getStudentId());
    }

    @Test
    void 다른_교수가_발급한_문서는_다운로드할_수_없다() {
        var user = new CurrentUser(7L, "PROFESSOR");
        when(professorCertificateClient.fetch(user)).thenReturn(career(List.of()));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(
                new Document(null, 99L, DocumentType.CAREER, TOKEN, "hash", "test.pdf")));
        assertThrows(CertificateNotEligibleException.class, () -> service.getCertificateDownloadUrl(user, 1L));
        verify(fileStorageService, never()).presignedDownloadUrl(any());
    }

    @Test
    void 강의경력은_별도_문서유형과_실제_강의기간으로_발급한다() {
        var user = new CurrentUser(7L, "PROFESSOR");
        var lecture = new ProfessorCareerResponse.Teaching(10L, (short)2025, "FIRST", "CS101", "자료구조", "01",
                (byte)3, java.time.LocalDate.of(2025, 3, 1), java.time.LocalDate.of(2025, 6, 30));
        when(professorCertificateClient.fetch(user)).thenReturn(career(List.of(lecture)));
        when(pdfGenerator.generate(any(), any(), any(), any())).thenReturn(new byte[]{1});
        when(fileStorageService.upload(any(), any(), any(), any())).thenReturn("certificates/lecture.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(DocumentType.LECTURE_CAREER, service.issueCareerCertificate(user, true).documentType());
        verify(pdfGenerator).generate(any(), argThat(rows -> rows.stream().anyMatch(row ->
                row.getValue().equals("2025-03-01 ~ 2025-06-30"))), any(), any());
    }

    private CurrentUser admin() {
        return new CurrentUser(1L, "ADMIN");
    }

    private CurrentUser studentWithEligibility(Boolean graduationSatisfied) {
        var user = new CurrentUser(7L, "STUDENT");
        when(tuitionBillService.resolveStudentId(user)).thenReturn(8L);
        when(academicResyncClient.fetchStudentCertificateEligibility(8L, user)).thenReturn(Optional.of(
                new com.msa4lmsv2payment.global.client.StudentCertificateEligibilityResponse(
                        8L, "22010001", "검증학생", "컴퓨터학과", "공과대학", (byte)4, (short)2022,
                        "ENROLLED", graduationSatisfied, 130)));
        return user;
    }

    private com.msa4lmsv2payment.domain.document.request.AcademicCertificateRequestDTO request(DocumentType type) {
        return new com.msa4lmsv2payment.domain.document.request.AcademicCertificateRequestDTO(type);
    }

    private void stubIssue() {
        when(pdfGenerator.generate(any(), any(), any(), any())).thenReturn(new byte[]{1});
        when(fileStorageService.upload(any(), any(), any(), any())).thenReturn("certificates/test.pdf");
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void 성적증명서는_졸업요건과_무관하게_공개성적과_재수강반영_요약을_기재한다() {
        var user = studentWithEligibility(false);
        var oldGrade = new com.msa4lmsv2payment.global.client.StudentGradeResponse.Grade(
                (short)2024, "FIRST", "CS101", "자료구조", (byte)3, "F", java.math.BigDecimal.ZERO, false);
        var latest = new com.msa4lmsv2payment.global.client.StudentGradeResponse.Grade(
                (short)2025, "SECOND", "CS101", "자료구조", (byte)3, "A+", new java.math.BigDecimal("4.5"), true);
        when(academicResyncClient.fetchStudentGrades(user)).thenReturn(Optional.of(
                new com.msa4lmsv2payment.global.client.StudentGradeResponse(new java.math.BigDecimal("4.50"), 3, List.of(oldGrade, latest))));
        stubIssue();
        assertEquals(DocumentType.GRADE, service.issueAcademicCertificate(user, request(DocumentType.GRADE)).documentType());
        verify(documentRepository).save(argThat(doc -> doc.getStudentId().equals(8L) && doc.getProfessorId() == null));
        verify(pdfGenerator).generate(org.mockito.ArgumentMatchers.eq("성 적 증 명 서"), argThat(rows ->
                rows.stream().anyMatch(row -> row.getValue().equals("3학점 / 4.50 (4.5 만점)"))
                && rows.stream().anyMatch(row -> row.getValue().contains("재수강으로 합계 제외"))
                && rows.stream().anyMatch(row -> row.getValue().contains("자료구조 (CS101)"))), any(), any());
    }

    @Test void 공개성적이_없으면_이유를_안내하고_발급하지_않는다() {
        var user = studentWithEligibility(null);
        when(academicResyncClient.fetchStudentGrades(user)).thenReturn(Optional.of(
                new com.msa4lmsv2payment.global.client.StudentGradeResponse(java.math.BigDecimal.ZERO, 0, List.of())));
        var error = assertThrows(CertificateNotEligibleException.class,
                () -> service.issueAcademicCertificate(user, request(DocumentType.GRADE)));
        assertTrue(error.getMessage().contains("강의평가"));
        verify(fileStorageService, never()).upload(any(), any(), any(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test void 학사_조회실패를_성적없음으로_취급하거나_문서를_발급하지_않는다() {
        var user = studentWithEligibility(null);
        when(academicResyncClient.fetchStudentGrades(user)).thenReturn(Optional.empty());
        assertThrows(com.msa4lmsv2payment.global.error.AcademicServiceUnavailableException.class,
                () -> service.issueAcademicCertificate(user, request(DocumentType.GRADE)));
        verify(documentRepository, never()).save(any());
        verify(fileStorageService, never()).upload(any(), any(), any(), any());
    }

    @Test void 졸업요건_충족은_졸업증명서로_발급하고_미충족은_저장하지_않는다() {
        var user = studentWithEligibility(false);
        assertThrows(CertificateNotEligibleException.class,
                () -> service.issueAcademicCertificate(user, request(DocumentType.GRADUATION)));
        verify(documentRepository, never()).save(any());
        studentWithEligibility(true);
        stubIssue();
        assertEquals(DocumentType.GRADUATION, service.issueAcademicCertificate(user, request(DocumentType.GRADUATION)).documentType());
        verify(pdfGenerator).generate(org.mockito.ArgumentMatchers.eq("졸 업 증 명 서"),
                argThat(rows -> rows.stream().anyMatch(row -> row.getValue().equals("충족(취득 130학점)"))), any(), any());
        verify(academicResyncClient, never()).fetchStudentGrades(any());
    }

    @Test void 다른_증명서_종류가_졸업증명서로_발급되지_않는다() {
        assertThrows(CertificateNotEligibleException.class,
                () -> service.issueAcademicCertificate(new CurrentUser(7L, "STUDENT"), request(DocumentType.CAREER)));
        verify(tuitionBillService, never()).resolveStudentId(any());
    }
}

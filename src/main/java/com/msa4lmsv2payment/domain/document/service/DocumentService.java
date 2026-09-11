package com.msa4lmsv2payment.domain.document.service;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerification;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerificationResult;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.repository.DocumentVerificationRepository;
import com.msa4lmsv2payment.domain.document.request.AcademicCertificateRequestDTO;
import com.msa4lmsv2payment.domain.document.request.DocumentRevokeRequestDTO;
import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.CertificateVerificationResponseDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicResyncClient;
import com.msa4lmsv2payment.global.client.ProfessorCertificateEligibilityResponse;
import com.msa4lmsv2payment.global.client.StudentCertificateEligibilityResponse;
import com.msa4lmsv2payment.global.document.CertificatePdfGenerator;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.error.CertificateNotEligibleException;
import com.msa4lmsv2payment.global.error.DocumentNotFoundException;
import com.msa4lmsv2payment.global.error.DocumentSignatureMismatchException;
import com.msa4lmsv2payment.global.error.PaymentNotCompletedException;
import com.msa4lmsv2payment.global.file.FileStorageService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private static final Map<String, String> ACADEMIC_STATUS_LABEL = Map.of(
            "ENROLLED", "재학", "LEAVE_OF_ABSENCE", "휴학", "GRADUATED", "졸업",
            "DISMISSED", "제적", "WITHDRAWN", "자퇴"
    );
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DocumentRepository documentRepository;
    private final DocumentVerificationRepository documentVerificationRepository;
    private final TuitionBillService tuitionBillService;
    private final PaymentService paymentService;
    private final AuditLogRecorder auditLogRecorder;
    private final TransactionTemplate requiresNewTransaction;
    private final AcademicResyncClient academicResyncClient;
    private final FileStorageService fileStorageService;
    private final CertificatePdfGenerator pdfGenerator;
    private final String signingKey;
    private final String clientBaseUrl;

    public DocumentService(DocumentRepository documentRepository, DocumentVerificationRepository documentVerificationRepository,
                            TuitionBillService tuitionBillService, PaymentService paymentService, AuditLogRecorder auditLogRecorder,
                            PlatformTransactionManager transactionManager, AcademicResyncClient academicResyncClient,
                            FileStorageService fileStorageService, CertificatePdfGenerator pdfGenerator,
                            @Value("${certificate.signing-key}") String signingKey,
                            @Value("${certificate.client-base-url}") String clientBaseUrl) {
        this.documentRepository = documentRepository;
        this.documentVerificationRepository = documentVerificationRepository;
        this.tuitionBillService = tuitionBillService;
        this.paymentService = paymentService;
        this.auditLogRecorder = auditLogRecorder;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.academicResyncClient = academicResyncClient;
        this.fileStorageService = fileStorageService;
        this.pdfGenerator = pdfGenerator;
        this.signingKey = signingKey;
        this.clientBaseUrl = clientBaseUrl;
    }

    // 납부 확인서 - 실제 납부 이력이 있는 고지에만 발급한다.
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponseDTO issuePaymentReceipt(CurrentUser currentUser, PaymentReceiptRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        if (!paymentService.hasSucceededPayment(tuitionBill.getId())) {
            throw new PaymentNotCompletedException("납부 이력이 없어 납부 확인서를 발급할 수 없습니다.");
        }

        List<Map.Entry<String, String>> rows = List.of(
                entry("학번", String.valueOf(tuitionBill.getStudentId())),
                entry("등록금 고지 ID", String.valueOf(tuitionBill.getId())),
                entry("납부 금액", tuitionBill.getBillingAmount() + "원")
        );
        return issue(tuitionBill.getStudentId(), null, DocumentType.PAYMENT_CERTIFICATE, "납 부 확 인 서", rows);
    }

    // 학생 재학/졸업증명서 - Academic의 학적·졸업요건 실시간 조회 결과로 자격을 확인한 뒤에만 발급한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponseDTO issueAcademicCertificate(CurrentUser currentUser, AcademicCertificateRequestDTO request) {
        Long studentId = tuitionBillService.resolveStudentId(currentUser);
        StudentCertificateEligibilityResponse eligibility = academicResyncClient
                .fetchStudentCertificateEligibility(studentId)
                .orElseThrow(() -> new AcademicResourceNotFoundException("학생 학적 정보를 확인할 수 없습니다."));

        String title;
        if (request.documentType() == DocumentType.ENROLLMENT) {
            if (!"ENROLLED".equals(eligibility.academicStatus())) {
                throw new CertificateNotEligibleException("재학 중인 학생만 재학증명서를 발급할 수 있습니다.");
            }
            title = "재 학 증 명 서";
        } else {
            if (!Boolean.TRUE.equals(eligibility.graduationSatisfied())) {
                throw new CertificateNotEligibleException("졸업요건을 충족하지 못해 졸업증명서를 발급할 수 없습니다.");
            }
            title = "졸 업 증 명 서";
        }

        List<Map.Entry<String, String>> rows = new ArrayList<>(List.of(
                entry("성명", eligibility.name()),
                entry("학번", eligibility.studentNumber()),
                entry("학과", eligibility.departmentName()),
                entry("단과대학", eligibility.collegeName()),
                entry("학년", eligibility.gradeLevel() + "학년"),
                entry("입학년도", eligibility.admissionYear() + "학년도"),
                entry("재학상태", ACADEMIC_STATUS_LABEL.getOrDefault(eligibility.academicStatus(), eligibility.academicStatus()))
        ));
        if (request.documentType() == DocumentType.GRADUATION) {
            rows.add(entry("졸업요건", "충족(취득 " + eligibility.earnedTotalCredits() + "학점)"));
        }

        return issue(studentId, null, request.documentType(), title, rows);
    }

    // 교수 재직증명서 - 재직 상태(ACTIVE)인 교수만 발급할 수 있다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponseDTO issueEmploymentCertificate(CurrentUser currentUser) {
        ProfessorCertificateEligibilityResponse eligibility = academicResyncClient
                .fetchProfessorCertificateEligibilityByUserId(currentUser.id())
                .orElseThrow(() -> new AcademicResourceNotFoundException("교수 정보를 확인할 수 없습니다."));

        if (!"ACTIVE".equals(eligibility.status())) {
            throw new CertificateNotEligibleException("재직 중인 교수만 재직증명서를 발급할 수 있습니다.");
        }

        List<Map.Entry<String, String>> rows = List.of(
                entry("성명", eligibility.name()),
                entry("교번", eligibility.professorNumber()),
                entry("학과", eligibility.departmentName()),
                entry("단과대학", eligibility.collegeName()),
                entry("임용연도", eligibility.hireYear() + "년"),
                entry("재직상태", "재직")
        );

        return issue(null, eligibility.professorId(), DocumentType.EMPLOYMENT, "재 직 증 명 서", rows);
    }

    // 세 발급 메서드가 공유하는 "토큰 발급 -> PDF 생성 -> MinIO 업로드 -> Document 저장" 공통 흐름.
    private DocumentResponseDTO issue(Long studentId, Long professorId, DocumentType documentType,
                                       String title, List<Map.Entry<String, String>> rows) {
        String verificationToken = UUID.randomUUID().toString();
        String signature = hmacSign(verificationToken);
        LocalDateTime issuedAt = LocalDateTime.now();
        String verifyUrl = clientBaseUrl + "/certificates/verify?token=" + verificationToken + "&qrHash=" + signature;

        byte[] pdfBytes = pdfGenerator.generate(title, rows, verifyUrl, issuedAt);
        String objectKey = fileStorageService.upload(
                "certificates/" + documentType.name().toLowerCase(), ".pdf", pdfBytes, "application/pdf");

        Document document = documentRepository.save(
                new Document(studentId, professorId, documentType, verificationToken, signature, objectKey));
        return DocumentResponseDTO.from(document);
    }

    // 발급된 증명서 PDF의 다운로드 URL(만료 1일)을 발급한다. 본인(발급받은 학생/교수) 또는 ADMIN만 가능하다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String getCertificateDownloadUrl(CurrentUser currentUser, Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("해당 증명서를 찾을 수 없습니다."));
        if (document.getFilePath() == null) {
            throw new DocumentNotFoundException("증명서 파일이 아직 생성되지 않았습니다.");
        }
        if (!isOwner(currentUser, document)) {
            throw new CertificateNotEligibleException("본인이 발급받은 증명서만 다운로드할 수 있습니다.");
        }
        return fileStorageService.presignedDownloadUrl(document.getFilePath());
    }

    private boolean isOwner(CurrentUser currentUser, Document document) {
        if ("ADMIN".equals(currentUser.role())) {
            return true;
        }
        if ("STUDENT".equals(currentUser.role()) && document.getStudentId() != null) {
            return document.getStudentId().equals(tuitionBillService.resolveStudentId(currentUser));
        }
        if ("PROFESSOR".equals(currentUser.role()) && document.getProfessorId() != null) {
            return academicResyncClient.fetchProfessorCertificateEligibilityByUserId(currentUser.id())
                    .map(ProfessorCertificateEligibilityResponse::professorId)
                    .map(document.getProfessorId()::equals)
                    .orElse(false);
        }
        return false;
    }

    // 제3자가 로그인 없이 QR/링크의 토큰과 서명만으로 조회하는 공개 API - 학생·교수 식별 정보는 응답에 담지 않는다.
    @Transactional
    public CertificateVerificationResponseDTO verifyCertificate(String token, String qrHash, String verifierIp) {
        Document document = documentRepository.findByVerificationToken(token)
                .orElseThrow(() -> new DocumentNotFoundException("유효하지 않은 검증 토큰입니다."));

        if (qrHash != null && !qrHash.isBlank() && !hmacSign(token).equals(qrHash)) {
            // 이 요청 자체는 실패로 끝나 롤백되므로, 위변조 시도 기록은 별도 트랜잭션으로 커밋해 남긴다.
            requiresNewTransaction.executeWithoutResult(status -> documentVerificationRepository.save(
                    new DocumentVerification(document.getId(), verifierIp, DocumentVerificationResult.SIGNATURE_MISMATCH)));
            throw new DocumentSignatureMismatchException("서명이 일치하지 않습니다.");
        }

        DocumentVerificationResult result = document.isRevoked() ? DocumentVerificationResult.REVOKED : DocumentVerificationResult.VALID;
        documentVerificationRepository.save(new DocumentVerification(document.getId(), verifierIp, result));

        return CertificateVerificationResponseDTO.of(document, result);
    }

    // ADMIN 전용 - 이미 발급된 증명서를 폐기해 이후 진위확인에서 REVOKED로 응답되게 한다.
    @Transactional
    public DocumentResponseDTO revokeDocument(CurrentUser admin, Long documentId, DocumentRevokeRequestDTO request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("해당 증명서를 찾을 수 없습니다."));

        document.revoke();

        auditLogRecorder.record(admin.id(), AuditAction.DOCUMENT_REVOKED, "DOCUMENT", document.getId(),
                Map.of("documentType", document.getDocumentType()), request.reason());

        return DocumentResponseDTO.from(document);
    }

    private Map.Entry<String, String> entry(String key, String value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

    // 토큰에 서버 비밀키로 HmacSHA256 서명을 계산한다. 평문 해시(과거 방식)와 달리 키 없이는 아무도 재계산할 수 없다.
    private String hmacSign(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("증명서 서명 계산에 실패했습니다.", e);
        }
    }
}

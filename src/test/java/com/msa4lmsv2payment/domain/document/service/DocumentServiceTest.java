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
import com.msa4lmsv2payment.global.error.DocumentAlreadyRevokedException;
import com.msa4lmsv2payment.global.error.DocumentNotFoundException;
import com.msa4lmsv2payment.global.error.DocumentSignatureMismatchException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";

    @Mock DocumentRepository documentRepository;
    @Mock DocumentVerificationRepository documentVerificationRepository;
    @Mock TuitionBillService tuitionBillService;
    @Mock PaymentService paymentService;
    @Mock AuditLogRecorder auditLogRecorder;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new DocumentService(documentRepository, documentVerificationRepository,
                tuitionBillService, paymentService, auditLogRecorder, transactionManager);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
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
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, sha256(TOKEN));
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        assertThrows(DocumentSignatureMismatchException.class,
                () -> service.verifyCertificate(TOKEN, "tampered-hash", "127.0.0.1"));
        verify(documentVerificationRepository).save(any());
    }

    @Test
    void qrHash가_토큰_해시와_일치하면_통과한다() throws Exception {
        Document document = new Document(1L, null, DocumentType.PAYMENT_CERTIFICATE, TOKEN, sha256(TOKEN));
        when(documentRepository.findByVerificationToken(TOKEN)).thenReturn(Optional.of(document));

        CertificateVerificationResponseDTO response = service.verifyCertificate(TOKEN, sha256(TOKEN), "127.0.0.1");

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

    private CurrentUser admin() {
        return new CurrentUser(1L, "ADMIN");
    }
}

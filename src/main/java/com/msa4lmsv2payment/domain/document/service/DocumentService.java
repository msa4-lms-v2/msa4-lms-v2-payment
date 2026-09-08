package com.msa4lmsv2payment.domain.document.service;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerification;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerificationResult;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.repository.DocumentVerificationRepository;
import com.msa4lmsv2payment.domain.document.request.DocumentRevokeRequestDTO;
import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.CertificateVerificationResponseDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.DocumentNotFoundException;
import com.msa4lmsv2payment.global.error.DocumentSignatureMismatchException;
import com.msa4lmsv2payment.global.error.PaymentNotCompletedException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVerificationRepository documentVerificationRepository;
    private final TuitionBillService tuitionBillService;
    private final PaymentService paymentService;
    private final AuditLogRecorder auditLogRecorder;

    // 납부 확인서 - 실제 납부 이력이 있는 고지에만 발급한다.
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponseDTO issuePaymentReceipt(CurrentUser currentUser, PaymentReceiptRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        if (!paymentService.hasSucceededPayment(tuitionBill.getId())) {
            throw new PaymentNotCompletedException("납부 이력이 없어 납부 확인서를 발급할 수 없습니다.");
        }

        String verificationToken = UUID.randomUUID().toString();
        Document document = documentRepository.save(new Document(
                tuitionBill.getStudentId(), null, DocumentType.PAYMENT_CERTIFICATE, verificationToken, hash(verificationToken)));

        return DocumentResponseDTO.from(document);
    }

    // 제3자가 로그인 없이 QR/링크의 토큰(과 있으면 서명 해시)만으로 조회하는 공개 API - 학생·교수 식별 정보는 응답에 담지 않는다.
    // qrHash를 넘기면 토큰의 SHA-256 해시와 대조해 위조 여부까지 확인한다(서명 검증). 넘기지 않으면 토큰 존재·폐기 여부만 확인한다.
    @Transactional
    public CertificateVerificationResponseDTO verifyCertificate(String token, String qrHash, String verifierIp) {
        Document document = documentRepository.findByVerificationToken(token)
                .orElseThrow(() -> new DocumentNotFoundException("유효하지 않은 검증 토큰입니다."));

        if (qrHash != null && !qrHash.isBlank() && !hash(token).equals(qrHash)) {
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

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}

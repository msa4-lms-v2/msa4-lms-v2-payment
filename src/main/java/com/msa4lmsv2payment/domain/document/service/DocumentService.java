package com.msa4lmsv2payment.domain.document.service;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.global.error.PaymentNotCompletedException;
import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final TuitionBillService tuitionBillService;
    private final PaymentService paymentService;

    // SCRUM-114: 납부 확인서 - 실제 납부 이력이 있는 고지에만 발급한다.
    // 소유권 검증이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
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

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}

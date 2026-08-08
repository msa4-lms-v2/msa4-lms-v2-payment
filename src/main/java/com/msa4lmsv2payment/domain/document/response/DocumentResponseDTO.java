package com.msa4lmsv2payment.domain.document.response;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;

import java.time.LocalDateTime;

public record DocumentResponseDTO(
        Long id,
        DocumentType documentType,
        String verificationToken,
        LocalDateTime issuedAt
) {
    public static DocumentResponseDTO from(Document document) {
        return new DocumentResponseDTO(
                document.getId(),
                document.getDocumentType(),
                document.getVerificationToken(),
                document.getIssuedAt()
        );
    }
}

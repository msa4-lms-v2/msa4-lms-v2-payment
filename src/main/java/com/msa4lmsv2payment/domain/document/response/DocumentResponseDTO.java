package com.msa4lmsv2payment.domain.document.response;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record DocumentResponseDTO(
        @Schema(description = "증명서 ID") Long id,
        @Schema(description = "증명서 종류") DocumentType documentType,
        @Schema(description = "진위확인용 검증 토큰") String verificationToken,
        @Schema(description = "발급 시각") LocalDateTime issuedAt
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

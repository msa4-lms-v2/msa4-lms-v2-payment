package com.msa4lmsv2payment.domain.document.response;

import com.msa4lmsv2payment.domain.document.entity.Document;
import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import com.msa4lmsv2payment.domain.document.entity.DocumentVerificationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// 제3자가 로그인 없이 조회하는 공개 응답이라 학생·교수 식별 정보는 담지 않는다.
public record CertificateVerificationResponseDTO(
        @Schema(description = "증명서 종류") DocumentType documentType,
        @Schema(description = "발급 시각") LocalDateTime issuedAt,
        @Schema(description = "검증 결과") DocumentVerificationResult result
) {
    public static CertificateVerificationResponseDTO of(Document document, DocumentVerificationResult result) {
        return new CertificateVerificationResponseDTO(document.getDocumentType(), document.getIssuedAt(), result);
    }
}

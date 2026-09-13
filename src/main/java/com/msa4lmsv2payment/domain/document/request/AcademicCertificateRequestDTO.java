package com.msa4lmsv2payment.domain.document.request;

import com.msa4lmsv2payment.domain.document.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학생 증명서 발급 요청")
public record AcademicCertificateRequestDTO(
        @Schema(description = "증명서 종류(ENROLLMENT: 재학증명서, GRADUATION: 졸업증명서)", example = "ENROLLMENT")
        @NotNull(message = "documentType은 필수입니다.")
        DocumentType documentType
) {
    @Schema(hidden = true)
    @AssertTrue(message = "documentType은 ENROLLMENT 또는 GRADUATION만 가능합니다.")
    public boolean isStudentDocumentType() {
        return documentType == null
                || documentType == DocumentType.ENROLLMENT
                || documentType == DocumentType.GRADUATION;
    }
}

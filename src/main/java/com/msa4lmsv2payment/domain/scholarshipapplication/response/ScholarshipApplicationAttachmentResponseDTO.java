package com.msa4lmsv2payment.domain.scholarshipapplication.response;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

public record ScholarshipApplicationAttachmentResponseDTO(
        @Schema(description = "첨부파일 ID", example = "1") Long id,
        @Schema(description = "원본 파일명", example = "가계곤란_증빙서류.pdf") String fileName,
        @Schema(description = "MIME 타입", example = "application/pdf") String contentType,
        @Schema(description = "파일 크기(byte)", example = "102400") long fileSize,
        @Schema(description = "1일간 유효한 임시 다운로드 URL") String downloadUrl
) {
    public static ScholarshipApplicationAttachmentResponseDTO from(ScholarshipApplicationAttachment attachment, String downloadUrl) {
        return new ScholarshipApplicationAttachmentResponseDTO(
                attachment.getId(), attachment.getFileName(), attachment.getContentType(),
                attachment.getFileSize(), downloadUrl);
    }
}

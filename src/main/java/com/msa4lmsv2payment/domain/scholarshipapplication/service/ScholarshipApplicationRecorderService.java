package com.msa4lmsv2payment.domain.scholarshipapplication.service;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationAttachment;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationAttachmentRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 장학금 신청 저장, 증빙 첨부파일 업로드, 감사 로그 기록을 하나의 트랜잭션으로 묶는다(TuitionBillRecorderService와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class ScholarshipApplicationRecorderService {

    private static final String ATTACHMENT_PATH_PREFIX = "scholarship-applications/attachments";

    private final ScholarshipApplicationRepository scholarshipApplicationRepository;
    private final ScholarshipApplicationAttachmentRepository scholarshipApplicationAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public ScholarshipApplication saveWithAudit(Long actorId, ScholarshipApplication application, List<MultipartFile> attachments) {
        ScholarshipApplication saved = scholarshipApplicationRepository.save(application);
        saveAttachments(saved, attachments);
        auditLogRecorder.record(actorId, AuditAction.SCHOLARSHIP_APPLICATION_REQUESTED, "SCHOLARSHIP_APPLICATION", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "requestedAmount", saved.getRequestedAmount()), null);
        return saved;
    }

    private void saveAttachments(ScholarshipApplication application, List<MultipartFile> attachments) {
        if (attachments == null) return;
        for (MultipartFile attachment : attachments) {
            if (attachment == null || attachment.isEmpty()) continue;
            String objectKey = fileStorageService.upload(ATTACHMENT_PATH_PREFIX, attachment);
            scholarshipApplicationAttachmentRepository.save(new ScholarshipApplicationAttachment(
                    application.getId(), attachment.getOriginalFilename(), objectKey,
                    attachment.getContentType(), attachment.getSize()));
        }
    }
}

package com.msa4lmsv2payment.domain.scholarshipapplication.service;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationRepository;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 장학금 신청 저장과 감사 로그 기록을 하나의 트랜잭션으로 묶는다(TuitionBillRecorderService와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class ScholarshipApplicationRecorderService {

    private final ScholarshipApplicationRepository scholarshipApplicationRepository;
    private final AuditLogRecorder auditLogRecorder;

    @Transactional
    public ScholarshipApplication saveWithAudit(Long actorId, ScholarshipApplication application) {
        ScholarshipApplication saved = scholarshipApplicationRepository.save(application);
        auditLogRecorder.record(actorId, AuditAction.SCHOLARSHIP_APPLICATION_REQUESTED, "SCHOLARSHIP_APPLICATION", saved.getId(),
                Map.of("tuitionBillId", saved.getTuitionBillId(), "requestedAmount", saved.getRequestedAmount()), null);
        return saved;
    }
}

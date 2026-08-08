package com.msa4lmsv2payment.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 7-6절 - "무엇이 기록됐는가"가 핵심이라 AOP 대신 서비스 계층에서 명시 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogRecorder {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(Long actorId, AuditAction action, String targetType, Long targetId, Object afterValue, String reason) {
        String afterValueJson = afterValue == null ? null : objectMapper.writeValueAsString(afterValue);
        auditLogRepository.save(new AuditLog(actorId, action.name(), targetType, targetId, afterValueJson, reason));
        log.info("[AUDIT] action={} actorId={} targetType={} targetId={}", action, actorId, targetType, targetId);
    }
}

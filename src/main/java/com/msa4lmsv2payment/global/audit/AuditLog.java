package com.msa4lmsv2payment.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결제·환불·증명서 폐기 등 업무 액션의 감사 로그 - MY-PLAN_payment.md 7-6절.
 * Auth·Academic도 각자 동일 스키마의 audit_logs를 소유한다(docs-v2/MSA-LMS_ARCHITECTURE.md 5절 설계 결정 9번).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    private String action;

    private String targetType;

    private Long targetId;

    @Column(columnDefinition = "json")
    private String beforeValue;

    @Column(columnDefinition = "json")
    private String afterValue;

    private String reason;

    private String requestId;

    private String ipAddress;

    @CreatedDate
    private LocalDateTime createdAt;

    public AuditLog(Long actorId, String action, String targetType, Long targetId, String afterValue, String reason) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.afterValue = afterValue;
        this.reason = reason;
    }
}

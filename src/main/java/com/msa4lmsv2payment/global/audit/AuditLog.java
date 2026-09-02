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
 * 결제·환불·증명서 폐기 등 업무 액션의 감사 로그.
 * Auth·Academic도 각자 동일 스키마의 audit_logs 테이블을 독립적으로 소유하며, 이 테이블과 공유하지 않는다.
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

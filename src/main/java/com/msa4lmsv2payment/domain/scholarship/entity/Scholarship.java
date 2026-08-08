package com.msa4lmsv2payment.domain.scholarship.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarships")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Scholarship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tuitionBillId;

    @Enumerated(EnumType.STRING)
    private ScholarshipType type;

    private BigDecimal amount;

    private String reason;

    private Long approvedBy;

    @CreatedDate
    private LocalDateTime createdAt;

    public Scholarship(Long tuitionBillId, ScholarshipType type, BigDecimal amount, String reason, Long approvedBy) {
        this.tuitionBillId = tuitionBillId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.approvedBy = approvedBy;
    }
}

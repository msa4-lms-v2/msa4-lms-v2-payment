package com.msa4lmsv2payment.domain.tuitionrate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "department_tuition_rates", uniqueConstraints = @UniqueConstraint(
        name = "uk_department_tuition_rates", columnNames = {"department_id", "semester_id"}))
public class DepartmentTuitionRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long departmentId;

    @Column(nullable = false)
    private Long semesterId;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String sourceUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public DepartmentTuitionRate(Long departmentId, Long semesterId, BigDecimal amount, String sourceUrl) {
        this.departmentId = departmentId;
        this.semesterId = semesterId;
        this.amount = amount;
        this.sourceUrl = sourceUrl;
    }
}

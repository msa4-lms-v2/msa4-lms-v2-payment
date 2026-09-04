package com.msa4lmsv2payment.domain.academicsnapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "withdrawal_snapshots")
public class WithdrawalSnapshot {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "withdrawal_id")
    private Long withdrawalId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}

package com.msa4lmsv2payment.domain.academicsnapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "student_snapshots")
public class StudentSnapshot {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "source_version", nullable = false)
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}

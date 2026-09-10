package com.msa4lmsv2payment.domain.scholarshipapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship_application_periods")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ScholarshipApplicationPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long semesterId;

    private LocalDate startDate;

    private LocalDate endDate;

    // Academic.academic_schedules.id 참조, FK 아님. 학사일정 공지와 연결할 때만 채움(선택) - 나중에 관리자 화면에서 학사일정 목록을 불러와 자동 연결하는 용도.
    private Long academicScheduleId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    private Long createdBy;

    @CreatedDate
    private LocalDateTime createdAt;

    public ScholarshipApplicationPeriod(Long semesterId, LocalDate startDate, LocalDate endDate,
                                         Long academicScheduleId, Long createdBy) {
        this.semesterId = semesterId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.academicScheduleId = academicScheduleId;
        this.createdBy = createdBy;
        this.active = true;
    }

    public boolean isOpenOn(LocalDate date) {
        return active && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public void changeFromAcademicSchedule(Long semesterId, LocalDate startDate, LocalDate endDate,
                                           boolean active) {
        this.semesterId = semesterId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
    }
}

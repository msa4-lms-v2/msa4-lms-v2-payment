package com.msa4lmsv2payment.domain.scholarshipapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "scholarship_application_attachments")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ScholarshipApplicationAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scholarshipApplicationId;

    private String fileName;

    private String objectKey;

    private String contentType;

    private long fileSize;

    @CreatedDate
    private LocalDateTime createdAt;

    public ScholarshipApplicationAttachment(Long scholarshipApplicationId, String fileName, String objectKey,
                                             String contentType, long fileSize) {
        this.scholarshipApplicationId = scholarshipApplicationId;
        this.fileName = fileName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }
}

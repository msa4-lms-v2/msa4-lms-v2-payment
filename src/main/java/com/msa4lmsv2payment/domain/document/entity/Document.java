package com.msa4lmsv2payment.domain.document.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "documents")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;

    private Long professorId;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    @CreatedDate
    private LocalDateTime issuedAt;

    private String filePath;

    private String verificationToken;

    private String qrHash;

    private LocalDateTime revokedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public Document(Long studentId, Long professorId, DocumentType documentType, String verificationToken, String qrHash) {
        this.studentId = studentId;
        this.professorId = professorId;
        this.documentType = documentType;
        this.verificationToken = verificationToken;
        this.qrHash = qrHash;
    }
}

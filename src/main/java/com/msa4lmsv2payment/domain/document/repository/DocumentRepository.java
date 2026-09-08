package com.msa4lmsv2payment.domain.document.repository;

import com.msa4lmsv2payment.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByVerificationToken(String verificationToken);
}

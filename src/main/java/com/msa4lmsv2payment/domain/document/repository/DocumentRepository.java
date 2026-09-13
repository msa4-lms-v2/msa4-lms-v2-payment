package com.msa4lmsv2payment.domain.document.repository;

import com.msa4lmsv2payment.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    org.springframework.data.domain.Page<Document> findByProfessorIdOrderByIssuedAtDescIdDesc(Long professorId, org.springframework.data.domain.Pageable pageable);
    Optional<Document> findByVerificationToken(String verificationToken);
}

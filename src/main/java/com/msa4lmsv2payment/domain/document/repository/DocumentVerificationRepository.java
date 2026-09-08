package com.msa4lmsv2payment.domain.document.repository;

import com.msa4lmsv2payment.domain.document.entity.DocumentVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVerificationRepository extends JpaRepository<DocumentVerification, Long> {
}

package com.msa4lmsv2payment.domain.scholarshipapplication.repository;

import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScholarshipApplicationAttachmentRepository extends JpaRepository<ScholarshipApplicationAttachment, Long> {

    List<ScholarshipApplicationAttachment> findByScholarshipApplicationIdOrderByIdAsc(Long scholarshipApplicationId);
}

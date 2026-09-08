package com.msa4lmsv2payment.domain.scholarshipapplication.service;

import com.msa4lmsv2payment.domain.installment.service.InstallmentPlanService;
import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationPeriodRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationDecision;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationReviewRequestDTO;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipApplicationServiceTest {

    @Mock ScholarshipApplicationRepository scholarshipApplicationRepository;
    @Mock ScholarshipApplicationPeriodRepository scholarshipApplicationPeriodRepository;
    @Mock TuitionBillService tuitionBillService;
    @Mock ScholarshipService scholarshipService;
    @Mock ScholarshipApplicationRecorderService scholarshipApplicationRecorder;
    @Mock AuditLogRecorder auditLogRecorder;
    @Mock InstallmentPlanService installmentPlanService;

    private ScholarshipApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ScholarshipApplicationService(scholarshipApplicationRepository, scholarshipApplicationPeriodRepository,
                tuitionBillService, scholarshipService, scholarshipApplicationRecorder, auditLogRecorder, installmentPlanService);
    }

    @Test
    void 승인하면_장학금을_적용하고_분할납부_회차를_재계산한다() {
        CurrentUser admin = new CurrentUser(9L, "ADMIN");
        ScholarshipApplication application = new ScholarshipApplication(
                10L, 20L, ScholarshipType.MERIT, BigDecimal.valueOf(200_000), "성적 우수");
        when(scholarshipApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(scholarshipService.applyScholarshipDiscount(eq(admin), any()))
                .thenReturn(new ScholarshipResponseDTO(100L, 10L, ScholarshipType.MERIT,
                        BigDecimal.valueOf(200_000), "장학금 신청 승인: 성적 우수", 9L, null));
        when(scholarshipService.calculateAllocation(eq(admin), any(PaymentScholarshipAllocationRequestDTO.class)))
                .thenReturn(new PaymentScholarshipAllocationResponseDTO(10L, BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(200_000), BigDecimal.valueOf(800_000)));

        service.reviewApplication(admin, 1L, new ScholarshipApplicationReviewRequestDTO(ScholarshipApplicationDecision.APPROVE, null));

        verify(installmentPlanService).recalculateForScholarshipChange(9L, 10L, BigDecimal.valueOf(800_000));
    }

    @Test
    void 반려하면_분할납부_재계산을_호출하지_않는다() {
        CurrentUser admin = new CurrentUser(9L, "ADMIN");
        ScholarshipApplication application = new ScholarshipApplication(
                10L, 20L, ScholarshipType.MERIT, BigDecimal.valueOf(200_000), "성적 우수");
        when(scholarshipApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        service.reviewApplication(admin, 1L,
                new ScholarshipApplicationReviewRequestDTO(ScholarshipApplicationDecision.REJECT, "서류 미비"));

        verify(installmentPlanService, org.mockito.Mockito.never())
                .recalculateForScholarshipChange(any(), any(), any());
    }
}

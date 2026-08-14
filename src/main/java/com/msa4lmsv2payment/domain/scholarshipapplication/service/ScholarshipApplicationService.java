package com.msa4lmsv2payment.domain.scholarshipapplication.service;

import com.msa4lmsv2payment.domain.scholarship.request.ScholarshipDiscountRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationPeriod;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationStatus;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationPeriodRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationCreateRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationDecision;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationPeriodCreateRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationReviewRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.response.ScholarshipApplicationPeriodResponseDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.response.ScholarshipApplicationResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditAction;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationAlreadyPendingException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationNotFoundException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationNotOpenException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationPeriodNotFoundException;
import com.msa4lmsv2payment.global.error.RejectReasonRequiredException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScholarshipApplicationService {

    private final ScholarshipApplicationRepository scholarshipApplicationRepository;
    private final ScholarshipApplicationPeriodRepository scholarshipApplicationPeriodRepository;
    private final TuitionBillService tuitionBillService;
    private final ScholarshipService scholarshipService;
    private final ScholarshipApplicationRecorder scholarshipApplicationRecorder;
    private final AuditLogRecorder auditLogRecorder;

    // 소유권 검증과 신청기간 판단(고지의 semesterId 확인)이 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ScholarshipApplicationResponseDTO createApplication(CurrentUser student, ScholarshipApplicationCreateRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(student, request.tuitionBillId());

        ScholarshipApplicationPeriod period = scholarshipApplicationPeriodRepository
                .findTopBySemesterIdOrderByCreatedAtDesc(tuitionBill.getSemesterId())
                .orElseThrow(() -> new ScholarshipApplicationPeriodNotFoundException("해당 학기의 장학금 신청기간이 설정되지 않았습니다."));
        if (!period.isOpenOn(LocalDate.now())) {
            throw new ScholarshipApplicationNotOpenException("장학금 신청기간이 아닙니다.");
        }

        scholarshipApplicationRepository.findByTuitionBillIdAndStatus(tuitionBill.getId(), ScholarshipApplicationStatus.REQUESTED)
                .ifPresent(existing -> {
                    throw new ScholarshipApplicationAlreadyPendingException("이미 심사 중인 장학금 신청이 있습니다.");
                });

        ScholarshipApplication application = new ScholarshipApplication(
                tuitionBill.getId(), tuitionBill.getStudentId(), request.type(), request.requestedAmount(), request.reason());

        return ScholarshipApplicationResponseDTO.from(scholarshipApplicationRecorder.saveWithAudit(student.id(), application));
    }

    public List<ScholarshipApplicationResponseDTO> getMyApplications(CurrentUser student) {
        return scholarshipApplicationRepository.findByStudentIdOrderByCreatedAtDesc(student.id()).stream()
                .map(ScholarshipApplicationResponseDTO::from)
                .toList();
    }

    // ADMIN 승인 시 scholarshipService.applyScholarshipDiscount를 내부 재사용해 고지금액 초과 방어를 그대로 물려받는다.
    @Transactional
    public ScholarshipApplicationResponseDTO reviewApplication(CurrentUser admin, Long applicationId, ScholarshipApplicationReviewRequestDTO request) {
        ScholarshipApplication application = getApplicationOrThrow(applicationId);

        if (request.decision() == ScholarshipApplicationDecision.APPROVE) {
            ScholarshipResponseDTO scholarship = scholarshipService.applyScholarshipDiscount(admin, new ScholarshipDiscountRequestDTO(
                    application.getTuitionBillId(), application.getType(), application.getRequestedAmount(),
                    "장학금 신청 승인: " + application.getReason()));
            application.approve(admin.id(), scholarship.id());
        } else {
            requireRejectReason(request.rejectReason());
            application.reject(admin.id(), request.rejectReason());
        }

        auditLogRecorder.record(admin.id(), AuditAction.SCHOLARSHIP_APPLICATION_REVIEWED, "SCHOLARSHIP_APPLICATION", application.getId(),
                Map.of("decision", request.decision().name()), request.rejectReason());

        return ScholarshipApplicationResponseDTO.from(application);
    }

    @Transactional
    public ScholarshipApplicationPeriodResponseDTO createApplicationPeriod(CurrentUser admin, ScholarshipApplicationPeriodCreateRequestDTO request) {
        ScholarshipApplicationPeriod period = scholarshipApplicationPeriodRepository.save(new ScholarshipApplicationPeriod(
                request.semesterId(), request.startDate(), request.endDate(), request.academicScheduleId(), admin.id()));
        return ScholarshipApplicationPeriodResponseDTO.from(period);
    }

    public ScholarshipApplicationPeriodResponseDTO getApplicationPeriod(Long semesterId) {
        ScholarshipApplicationPeriod period = scholarshipApplicationPeriodRepository
                .findTopBySemesterIdOrderByCreatedAtDesc(semesterId)
                .orElseThrow(() -> new ScholarshipApplicationPeriodNotFoundException("해당 학기의 장학금 신청기간이 설정되지 않았습니다."));
        return ScholarshipApplicationPeriodResponseDTO.from(period);
    }

    private ScholarshipApplication getApplicationOrThrow(Long applicationId) {
        return scholarshipApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ScholarshipApplicationNotFoundException("해당 장학금 신청을 찾을 수 없습니다."));
    }

    private void requireRejectReason(String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new RejectReasonRequiredException("반려 시 반려 사유는 필수입니다.");
        }
    }
}

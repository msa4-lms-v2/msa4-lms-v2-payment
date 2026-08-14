package com.msa4lmsv2payment.domain.scholarshipapplication.service;

import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.service.ScholarshipService;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplication;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationPeriod;
import com.msa4lmsv2payment.domain.scholarshipapplication.entity.ScholarshipApplicationStatus;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationPeriodRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.repository.ScholarshipApplicationRepository;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationCreateRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationDecision;
import com.msa4lmsv2payment.domain.scholarshipapplication.request.ScholarshipApplicationReviewRequestDTO;
import com.msa4lmsv2payment.domain.scholarshipapplication.response.ScholarshipApplicationResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.error.RejectReasonRequiredException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationAlreadyPendingException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationAlreadyReviewedException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationNotOpenException;
import com.msa4lmsv2payment.global.error.ScholarshipApplicationPeriodNotFoundException;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipApplicationServiceTest {

    @Mock
    private ScholarshipApplicationRepository scholarshipApplicationRepository;
    @Mock
    private ScholarshipApplicationPeriodRepository scholarshipApplicationPeriodRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private ScholarshipService scholarshipService;
    @Mock
    private ScholarshipApplicationRecorder scholarshipApplicationRecorder;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    @InjectMocks
    private ScholarshipApplicationService scholarshipApplicationService;

    private static TuitionBill tuitionBill(Long id, Long semesterId) {
        TuitionBill bill = new TuitionBill(20260001L, semesterId, BigDecimal.valueOf(4_200_000),
                LocalDate.of(2026, 8, 25), TuitionBillStatus.UNPAID, 1L);
        setField(TuitionBill.class, bill, "id", id);
        return bill;
    }

    private static ScholarshipApplicationPeriod period(Long semesterId, LocalDate start, LocalDate end) {
        return new ScholarshipApplicationPeriod(semesterId, start, end, null, 1L);
    }

    private static ScholarshipApplication application(Long id, Long tuitionBillId, ScholarshipApplicationStatus status) {
        ScholarshipApplication application = new ScholarshipApplication(
                tuitionBillId, 20260001L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "가계 곤란");
        setField(ScholarshipApplication.class, application, "id", id);
        setField(ScholarshipApplication.class, application, "status", status);
        return application;
    }

    private static <T> void setField(Class<T> type, T target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 신청기간_안이고_대기중인_신청이_없으면_신청이_생성된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, 5L);
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipApplicationPeriodRepository.findTopBySemesterIdOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(period(5L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        when(scholarshipApplicationRepository.findByTuitionBillIdAndStatus(1L, ScholarshipApplicationStatus.REQUESTED))
                .thenReturn(Optional.empty());
        when(scholarshipApplicationRecorder.saveWithAudit(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        ScholarshipApplicationResponseDTO result = scholarshipApplicationService.createApplication(student,
                new ScholarshipApplicationCreateRequestDTO(1L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "가계 곤란"));

        assertThat(result.status()).isEqualTo(ScholarshipApplicationStatus.REQUESTED);
        assertThat(result.tuitionBillId()).isEqualTo(1L);
    }

    @Test
    void 신청기간이_아니면_거부된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, 5L);
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipApplicationPeriodRepository.findTopBySemesterIdOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(period(5L, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3))));

        assertThatThrownBy(() -> scholarshipApplicationService.createApplication(student,
                new ScholarshipApplicationCreateRequestDTO(1L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "가계 곤란")))
                .isInstanceOf(ScholarshipApplicationNotOpenException.class);
    }

    @Test
    void 신청기간이_설정되지_않았으면_404() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, 5L);
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipApplicationPeriodRepository.findTopBySemesterIdOrderByCreatedAtDesc(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scholarshipApplicationService.createApplication(student,
                new ScholarshipApplicationCreateRequestDTO(1L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "가계 곤란")))
                .isInstanceOf(ScholarshipApplicationPeriodNotFoundException.class);
    }

    @Test
    void 이미_심사중인_신청이_있으면_거부된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        TuitionBill bill = tuitionBill(1L, 5L);
        when(tuitionBillService.getOwnedTuitionBillOrThrow(student, 1L)).thenReturn(bill);
        when(scholarshipApplicationPeriodRepository.findTopBySemesterIdOrderByCreatedAtDesc(5L))
                .thenReturn(Optional.of(period(5L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
        when(scholarshipApplicationRepository.findByTuitionBillIdAndStatus(1L, ScholarshipApplicationStatus.REQUESTED))
                .thenReturn(Optional.of(application(1L, 1L, ScholarshipApplicationStatus.REQUESTED)));

        assertThatThrownBy(() -> scholarshipApplicationService.createApplication(student,
                new ScholarshipApplicationCreateRequestDTO(1L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "가계 곤란")))
                .isInstanceOf(ScholarshipApplicationAlreadyPendingException.class);
    }

    @Test
    void 승인하면_장학금_감면_적용을_재사용하고_신청_상태가_승인된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        ScholarshipApplication application = application(1L, 1L, ScholarshipApplicationStatus.REQUESTED);
        when(scholarshipApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(scholarshipService.applyScholarshipDiscount(any(), any()))
                .thenReturn(new ScholarshipResponseDTO(99L, 1L, ScholarshipType.NEED_BASED, BigDecimal.valueOf(1_000_000), "사유", 1L, null));

        ScholarshipApplicationResponseDTO result = scholarshipApplicationService.reviewApplication(admin, 1L,
                new ScholarshipApplicationReviewRequestDTO(ScholarshipApplicationDecision.APPROVE, null));

        assertThat(result.status()).isEqualTo(ScholarshipApplicationStatus.APPROVED);
        assertThat(result.scholarshipId()).isEqualTo(99L);
    }

    @Test
    void 반려시_사유가_없으면_거부된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        ScholarshipApplication application = application(1L, 1L, ScholarshipApplicationStatus.REQUESTED);
        when(scholarshipApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> scholarshipApplicationService.reviewApplication(admin, 1L,
                new ScholarshipApplicationReviewRequestDTO(ScholarshipApplicationDecision.REJECT, null)))
                .isInstanceOf(RejectReasonRequiredException.class);
    }

    @Test
    void 이미_심사완료된_신청은_다시_심사할_수_없다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        ScholarshipApplication application = application(1L, 1L, ScholarshipApplicationStatus.APPROVED);
        when(scholarshipApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> scholarshipApplicationService.reviewApplication(admin, 1L,
                new ScholarshipApplicationReviewRequestDTO(ScholarshipApplicationDecision.REJECT, "사유")))
                .isInstanceOf(ScholarshipApplicationAlreadyReviewedException.class);
    }
}

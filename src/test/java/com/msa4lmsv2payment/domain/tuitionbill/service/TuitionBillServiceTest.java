package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.global.error.TuitionBillAccessDeniedException;
import com.msa4lmsv2payment.global.error.TuitionBillNotFoundException;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillQueryRepository;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.request.TuitionBillCreateRequestDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicStudentExistsResponse;
import com.msa4lmsv2payment.global.client.AcademicStudentResponse;
import com.msa4lmsv2payment.global.error.AcademicResourceNotFoundException;
import com.msa4lmsv2payment.global.response.PageRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TuitionBillServiceTest {

    @Mock
    private TuitionBillRepository tuitionBillRepository;
    @Mock
    private TuitionBillQueryRepository tuitionBillQueryRepository;
    @Mock
    private AcademicClient academicClient;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    @InjectMocks
    private TuitionBillService tuitionBillService;

    private static TuitionBill tuitionBill(Long id, Long studentId, BigDecimal amount, TuitionBillStatus status) {
        TuitionBill bill = new TuitionBill(studentId, 1L, amount, LocalDate.of(2026, 9, 1), status, 1L);
        setField(bill, "id", id);
        return bill;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = TuitionBill.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // SCRUM-73: 관리자 등록금 목록 조회
    @Test
    void 관리자_목록_조회는_전체_건수와_hasNext를_정확히_계산한다() {
        when(tuitionBillQueryRepository.search(eq(TuitionBillStatus.UNPAID), eq(0), eq(1)))
                .thenReturn(List.of(tuitionBill(1L, 1L, BigDecimal.valueOf(4_200_000), TuitionBillStatus.UNPAID)));
        when(tuitionBillQueryRepository.count(TuitionBillStatus.UNPAID)).thenReturn(3L);

        PageRes<TuitionBillResponseDTO> result = tuitionBillService.getAdminTuitionBills(TuitionBillStatus.UNPAID, 1, 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(3L);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 관리자_목록_조회_size는_상한_100으로_clamp된다() {
        when(tuitionBillQueryRepository.search(any(), eq(0), eq(100))).thenReturn(List.of());
        when(tuitionBillQueryRepository.count(any())).thenReturn(0L);

        PageRes<TuitionBillResponseDTO> result = tuitionBillService.getAdminTuitionBills(null, 1, 500);

        assertThat(result.size()).isEqualTo(100);
    }

    // SCRUM-76: 학생별 등록금 조회 - AcademicClient로 본인 학번을 조회해 그 학번의 고지만 반환
    @Test
    void 학생_본인_조회는_AcademicClient가_돌려준_학번의_고지만_가져온다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(academicClient.findStudentByUserId(1L)).thenReturn(new AcademicStudentResponse(20260001L, 1L, "ENROLLED"));
        when(tuitionBillRepository.findByStudentIdOrderByDueDateDesc(20260001L))
                .thenReturn(List.of(tuitionBill(4L, 20260001L, BigDecimal.valueOf(4_200_000), TuitionBillStatus.UNPAID)));

        List<TuitionBillResponseDTO> result = tuitionBillService.getMyTuitionBills(student);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentId()).isEqualTo(20260001L);
    }

    // SCRUM-75: 등록금 납부 상태 조회 - ADMIN은 전체, STUDENT는 본인 것만
    @Test
    void ADMIN은_소유권_검증_없이_모든_고지를_조회할_수_있다() {
        CurrentUser admin = new CurrentUser(99L, "ADMIN");
        when(tuitionBillRepository.findById(1L))
                .thenReturn(Optional.of(tuitionBill(1L, 20260001L, BigDecimal.valueOf(4_200_000), TuitionBillStatus.UNPAID)));

        var result = tuitionBillService.getTuitionPaymentStatus(admin, 1L);

        assertThat(result.tuitionBillId()).isEqualTo(1L);
    }

    @Test
    void STUDENT는_본인_소유가_아닌_고지를_조회하면_거부된다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(tuitionBillRepository.findById(1L))
                .thenReturn(Optional.of(tuitionBill(1L, 20260099L, BigDecimal.valueOf(4_200_000), TuitionBillStatus.UNPAID)));
        when(academicClient.findStudentByUserId(1L)).thenReturn(new AcademicStudentResponse(20260001L, 1L, "ENROLLED"));

        assertThatThrownBy(() -> tuitionBillService.getTuitionPaymentStatus(student, 1L))
                .isInstanceOf(TuitionBillAccessDeniedException.class);
    }

    @Test
    void 존재하지_않는_고지를_조회하면_TuitionBillNotFoundException() {
        when(tuitionBillRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tuitionBillService.getTuitionBillOrThrow(999L))
                .isInstanceOf(TuitionBillNotFoundException.class);
    }

    // SCRUM-43: 관리자 등록금 고지
    @Test
    void 등록금_고지_생성은_학생과_학기_존재를_Academic에_확인한다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        when(academicClient.findStudent(20260001L)).thenReturn(new AcademicStudentExistsResponse(20260001L, "ENROLLED"));

        var request = new TuitionBillCreateRequestDTO(20260001L, 5L, BigDecimal.valueOf(4_500_000), LocalDate.of(2026, 9, 15));
        var result = tuitionBillService.createTuitionBill(admin, request);

        assertThat(result.studentId()).isEqualTo(20260001L);
        assertThat(result.status()).isEqualTo(TuitionBillStatus.UNPAID);
    }

    @Test
    void 존재하지_않는_학생으로_등록금_고지를_생성하면_거부된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        when(academicClient.findStudent(999999L))
                .thenThrow(new AcademicResourceNotFoundException("존재하지 않는 학번입니다: 999999"));

        var request = new TuitionBillCreateRequestDTO(999999L, 5L, BigDecimal.valueOf(4_500_000), LocalDate.of(2026, 9, 15));

        assertThatThrownBy(() -> tuitionBillService.createTuitionBill(admin, request))
                .isInstanceOf(AcademicResourceNotFoundException.class);
    }

    // SCRUM-77: 학생 등록금 고지서 조회
    @Test
    void 학생은_본인_고지서를_단건_조회할_수_있다() {
        CurrentUser student = new CurrentUser(1L, "STUDENT");
        when(tuitionBillRepository.findById(5L))
                .thenReturn(Optional.of(tuitionBill(5L, 20260001L, BigDecimal.valueOf(4_500_000), TuitionBillStatus.UNPAID)));
        when(academicClient.findStudentByUserId(1L)).thenReturn(new AcademicStudentResponse(20260001L, 1L, "ENROLLED"));

        var result = tuitionBillService.getStudentTuitionBill(student, 5L);

        assertThat(result.id()).isEqualTo(5L);
    }
}

package com.msa4lmsv2payment.domain.scholarship.service;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import com.msa4lmsv2payment.domain.scholarship.entity.ScholarshipType;
import com.msa4lmsv2payment.global.error.ScholarshipExceedsBillingAmountException;
import com.msa4lmsv2payment.domain.scholarship.repository.ScholarshipRepository;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.request.ScholarshipDiscountRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipServiceTest {

    @Mock
    private ScholarshipRepository scholarshipRepository;
    @Mock
    private TuitionBillService tuitionBillService;

    @InjectMocks
    private ScholarshipService scholarshipService;

    private static TuitionBill tuitionBill(Long id, BigDecimal amount) {
        TuitionBill bill = new TuitionBill(20260001L, 1L, amount, LocalDate.of(2026, 9, 1), TuitionBillStatus.UNPAID, 1L);
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

    // SCRUM-81: 장학금 감면·면제 적용
    @Test
    void 기존_장학금과_합해_등록금을_넘지_않으면_생성된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getTuitionBillOrThrow(1L)).thenReturn(bill);
        when(scholarshipRepository.findByTuitionBillId(1L)).thenReturn(List.of());
        when(scholarshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new ScholarshipDiscountRequestDTO(1L, ScholarshipType.MERIT, BigDecimal.valueOf(2_000_000), "성적우수");
        var result = scholarshipService.applyScholarshipDiscount(admin, request);

        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
    }

    @Test
    void 기존_장학금과_합쳐_등록금을_넘으면_거부된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getTuitionBillOrThrow(1L)).thenReturn(bill);
        Scholarship existing = new Scholarship(1L, ScholarshipType.MERIT, BigDecimal.valueOf(2_000_000), "기존", 1L);
        when(scholarshipRepository.findByTuitionBillId(1L)).thenReturn(List.of(existing));

        var request = new ScholarshipDiscountRequestDTO(1L, ScholarshipType.OTHER, BigDecimal.valueOf(2_500_000), "초과분");

        assertThatThrownBy(() -> scholarshipService.applyScholarshipDiscount(admin, request))
                .isInstanceOf(ScholarshipExceedsBillingAmountException.class);
    }

    @Test
    void 합계가_등록금과_정확히_같으면_허용된다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getTuitionBillOrThrow(1L)).thenReturn(bill);
        Scholarship existing = new Scholarship(1L, ScholarshipType.MERIT, BigDecimal.valueOf(2_000_000), "기존", 1L);
        when(scholarshipRepository.findByTuitionBillId(1L)).thenReturn(List.of(existing));
        when(scholarshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new ScholarshipDiscountRequestDTO(1L, ScholarshipType.OTHER, BigDecimal.valueOf(2_200_000), "딱맞음");

        var result = scholarshipService.applyScholarshipDiscount(admin, request);
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(2_200_000));
    }

    // SCRUM-82: 실제 납부액과 장학금 구분
    @Test
    void 실납부액은_고지금액에서_장학금_합계를_뺀_값이다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        Scholarship s1 = new Scholarship(1L, ScholarshipType.MERIT, BigDecimal.valueOf(2_000_000), "성적", 1L);
        when(scholarshipRepository.findByTuitionBillId(1L)).thenReturn(List.of(s1));

        PaymentScholarshipAllocationResponseDTO result =
                scholarshipService.calculateAllocation(admin, new PaymentScholarshipAllocationRequestDTO(1L));

        assertThat(result.totalScholarshipAmount()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        assertThat(result.actualPaymentAmount()).isEqualByComparingTo(BigDecimal.valueOf(2_200_000));
    }

    @Test
    void 장학금_합계가_고지금액을_넘는_이상_데이터여도_실납부액은_음수가_되지_않는다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(1_000_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        Scholarship over = new Scholarship(1L, ScholarshipType.OTHER, BigDecimal.valueOf(1_500_000), "이상데이터", 1L);
        when(scholarshipRepository.findByTuitionBillId(1L)).thenReturn(List.of(over));

        PaymentScholarshipAllocationResponseDTO result =
                scholarshipService.calculateAllocation(admin, new PaymentScholarshipAllocationRequestDTO(1L));

        assertThat(result.actualPaymentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

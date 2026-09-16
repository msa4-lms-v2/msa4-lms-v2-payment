package com.msa4lmsv2payment.domain.admission;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillRecorderService;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillItemSpec;
import com.msa4lmsv2payment.domain.tuitionrate.entity.DepartmentTuitionRate;
import com.msa4lmsv2payment.domain.tuitionrate.repository.DepartmentTuitionRateRepository;
import com.msa4lmsv2payment.domain.tuitionrate.service.DepartmentTuitionRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdmissionTuitionRateTest {
    private final TuitionBillRepository bills = mock(TuitionBillRepository.class);
    private final TuitionBillRecorderService billRecorder = mock(TuitionBillRecorderService.class);
    private final DepartmentTuitionRateRepository rates = mock(DepartmentTuitionRateRepository.class);
    private final DepartmentTuitionRateService rateService = new DepartmentTuitionRateService(rates);
    private final AdmissionPaymentRecorder recorder = new AdmissionPaymentRecorder(
            bills, billRecorder, null, null, null, null, null, rateService);
    private final LocalDate dueDate = LocalDate.now().plusDays(10);

    private DepartmentTuitionRate engineeringRate() {
        var rate = new DepartmentTuitionRate(1L, 20262L, new BigDecimal("2998000"), "https://www.snu.ac.kr");
        ReflectionTestUtils.setField(rate, "id", 10L);
        return rate;
    }

    @Test
    void serverRateOverridesManipulatedClientAmountAndCreatesMatchingBillItem() {
        var rate = engineeringRate();
        when(rates.findByDepartmentIdAndSemesterId(1L, 20262L)).thenReturn(Optional.of(rate));
        when(billRecorder.saveWithAudit(eq(9L), any(), any())).thenAnswer(invocation -> {
            TuitionBill bill = invocation.getArgument(1);
            List<TuitionBillItemSpec> items = invocation.getArgument(2);
            assertThat(items).containsExactly(new TuitionBillItemSpec("입학 등록금", rate.getAmount()));
            return bill;
        });
        var request = new AdmissionBillRequest(20262L, BigDecimal.ONE, dueDate, "88");

        var bill = recorder.reserve(7L, "테스트", 9L, 1L, request);

        assertThat(bill.getBillingAmount()).isEqualByComparingTo("2998000");
        assertThat(bill.getTuitionRateId()).isEqualTo(10L);
    }

    @Test
    void amountMayBeOmittedByNewClient() {
        var request = new AdmissionBillRequest(20262L, null, dueDate, "88");
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        when(rates.findByDepartmentIdAndSemesterId(1L, 20262L)).thenReturn(Optional.of(engineeringRate()));
        when(billRecorder.saveWithAudit(anyLong(), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        assertThat(recorder.reserve(7L, "테스트", 9L, 1L, request).getBillingAmount())
                .isEqualByComparingTo("2998000");
    }

    @Test
    void missingRatePreventsBillCreation() {
        assertThatThrownBy(() -> recorder.reserve(7L, "테스트", 9L, 1L,
                new AdmissionBillRequest(20271L, BigDecimal.ONE, dueDate, "88")))
                .isInstanceOf(AdmissionPaymentConflictException.class)
                .hasMessageContaining("등록되지 않았습니다");
        verifyNoInteractions(billRecorder);
    }

    @Test
    void retryPreservesIssuedAmountEvenIfRateAmountChanges() {
        var existing = new TuitionBill(null, 20262L, new BigDecimal("2900000"), dueDate, TuitionBillStatus.UNPAID, 9L);
        existing.admission(7L, "테스트", "88");
        existing.assignTuitionRate(10L);
        when(bills.findByAdmissionCandidateId(7L)).thenReturn(Optional.of(existing));
        when(rates.findById(10L)).thenReturn(Optional.of(engineeringRate()));

        assertThat(recorder.reserve(7L, "테스트", 9L, 1L,
                new AdmissionBillRequest(20262L, null, dueDate, "88"))).isSameAs(existing);
        assertThat(existing.getBillingAmount()).isEqualByComparingTo("2900000");
        verify(rates, never()).findByDepartmentIdAndSemesterId(any(), any());
        verifyNoInteractions(billRecorder);
    }

    @Test
    void legacyBillCanRetryWithoutAnyRateRecord() {
        var existing = new TuitionBill(null, 20262L, new BigDecimal("3000000"), dueDate, TuitionBillStatus.UNPAID, 9L);
        existing.admission(7L, "테스트", "88");
        when(bills.findByAdmissionCandidateId(7L)).thenReturn(Optional.of(existing));
        assertThat(recorder.reserve(7L, "테스트", 9L, 1L,
                new AdmissionBillRequest(20262L, null, dueDate, "88"))).isSameAs(existing);
        verifyNoInteractions(rates, billRecorder);
    }

    @Test
    void changedDepartmentCannotReuseBillReservedForAnotherDepartment() {
        var existing = new TuitionBill(null, 20262L, new BigDecimal("2998000"), dueDate, TuitionBillStatus.UNPAID, 9L);
        existing.admission(7L, "테스트", "88");
        existing.assignTuitionRate(10L);
        when(bills.findByAdmissionCandidateId(7L)).thenReturn(Optional.of(existing));
        when(rates.findById(10L)).thenReturn(Optional.of(engineeringRate()));
        assertThatThrownBy(() -> recorder.reserve(7L, "테스트", 9L, 5L,
                new AdmissionBillRequest(20262L, null, dueDate, "88")))
                .isInstanceOf(AdmissionPaymentConflictException.class).hasMessageContaining("학과");
        verifyNoInteractions(billRecorder);
    }
}

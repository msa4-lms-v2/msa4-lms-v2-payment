package com.msa4lmsv2payment.domain.tuitionbill;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TuitionBillResponseTest {
    @Test
    void admissionBillCanIdentifyPayerBeforeStudentIsProvisioned() {
        var bill = new TuitionBill(null, 20262L, new BigDecimal("3653000"),
                LocalDate.of(2026, 9, 21), TuitionBillStatus.UNPAID, 1L);
        bill.admission(12L, "테스트예정자", "88");

        var response = TuitionBillResponseDTO.from(bill);

        assertThat(response.studentId()).isNull();
        assertThat(response.admissionCandidateId()).isEqualTo(12L);
        assertThat(response.admissionCustomerName()).isEqualTo("테스트예정자");
        assertThat(response.billingAmount()).isEqualByComparingTo("3653000");
    }
}

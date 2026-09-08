package com.msa4lmsv2payment.domain.payment;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.payment.response.PaymentResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.error.TuitionBillNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-180 - 관리자 환불·PG취소 관리 화면이 취소 대상 결제를 고를 수 있도록,
 * 등록금 고지 1건의 결제 이력을 조회하는 관리자 전용 조회를 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentListForBillIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void listsAllPaymentAttemptsForTheBill() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(31L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));

        Payment failed = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        failed.fail();
        paymentRepository.save(failed);

        Payment succeeded = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        succeeded.succeed("pg-key-list-1");
        paymentRepository.save(succeeded);

        List<PaymentResponseDTO> result = paymentService.listPaymentsForBill(bill.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PaymentResponseDTO::status)
                .containsExactlyInAnyOrder(PaymentStatus.FAILED, PaymentStatus.SUCCEEDED);
    }

    @Test
    void throwsWhenTuitionBillDoesNotExist() {
        assertThatThrownBy(() -> paymentService.listPaymentsForBill(999_999L))
                .isInstanceOf(TuitionBillNotFoundException.class);
    }
}

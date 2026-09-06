package com.msa4lmsv2payment.domain.payment;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.payment.request.PaymentStatusRequestDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.domain.refund.entity.Refund;
import com.msa4lmsv2payment.domain.refund.entity.RefundStatus;
import com.msa4lmsv2payment.domain.refund.entity.RefundType;
import com.msa4lmsv2payment.domain.refund.repository.RefundRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.security.CurrentUser;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRUM-132 - 환불이 완료되면 tuition_bills.status가 PAID에 머물지 않고 잔액 기준으로 다시 계산되는지 검증한다.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PaymentStatusRecalculationIntegrationTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Test
    void refundedBillIsRecalculatedFromPaidToPartial() {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(30L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));

        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(1_000_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pg-key-1");
        paymentRepository.save(payment);

        paymentService.recalculateTuitionStatus(ADMIN, new PaymentStatusRequestDTO(bill.getId()));
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.PAID);

        Refund refund = new Refund(bill.getId(), RefundType.PG_CANCEL, BigDecimal.valueOf(400_000), BigDecimal.ONE, RefundStatus.REQUESTED);
        refund.succeed();
        refundRepository.save(refund);

        paymentService.recalculateTuitionStatus(ADMIN, new PaymentStatusRequestDTO(bill.getId()));
        assertThat(tuitionBillRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(TuitionBillStatus.PARTIAL);
    }
}

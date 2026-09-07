package com.msa4lmsv2payment.domain.payment;

import com.msa4lmsv2payment.domain.payment.entity.Payment;
import com.msa4lmsv2payment.domain.payment.entity.PaymentMethod;
import com.msa4lmsv2payment.domain.payment.entity.PaymentStatus;
import com.msa4lmsv2payment.domain.payment.repository.PaymentRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-176 - Academic이 인증 헤더 없이 호출하는 시스템 간 납부 상태 조회를 검증한다.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAcademicProvisionIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void returnsTuitionStatusWithoutAnyGatewayHeaders() throws Exception {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(70L, 5L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        Payment payment = new Payment(bill.getId(), bill.getStudentId(), BigDecimal.valueOf(400_000), PaymentMethod.CARD, PaymentStatus.REQUESTED);
        payment.succeed("pk-academic-provision-1");
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/payment/academic-provision/students/{studentId}/semesters/{semesterId}/tuition-status",
                        bill.getStudentId(), bill.getSemesterId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tuitionBillId").value(bill.getId()))
                .andExpect(jsonPath("$.data.totalPaid").value(400000))
                .andExpect(jsonPath("$.data.remainingAmount").value(600000))
                .andExpect(jsonPath("$.data.hasPendingRefund").value(false));
    }

    @Test
    void returnsNotFoundWhenNoTuitionBillExistsForStudentAndSemester() throws Exception {
        mockMvc.perform(get("/api/payment/academic-provision/students/{studentId}/semesters/{semesterId}/tuition-status", 999L, 999L))
                .andExpect(status().isNotFound());
    }
}

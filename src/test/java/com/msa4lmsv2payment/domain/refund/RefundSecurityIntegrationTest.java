package com.msa4lmsv2payment.domain.refund;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicStudentResponse;
import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-174 - 환불 API의 실제 SecurityFilterChain을 태워 역할별 401/403 경계를 검증한다.
 * Gateway가 검증 후 전달하는 X-User-Id/X-User-Role 헤더를 직접 흉내낸다(GatewayContextAuthenticationFilter).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefundSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    @MockitoBean
    private AcademicClient academicClient;

    @Test
    void listRefundsWithoutGatewayHeadersIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/payment/refunds").param("tuitionBillId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pgCancelRequestByStudentIsForbidden() throws Exception {
        mockMvc.perform(post("/api/payment/refunds/pg-cancel-requests")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT")
                        .contentType("application/json")
                        .content("""
                                {"paymentId":1,"reason":"단순변심"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void executeRefundByStudentIsForbidden() throws Exception {
        mockMvc.perform(post("/api/payment/refunds/1/execute")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT")
                        .header("Idempotency-Key", "test-key")
                        .contentType("application/json")
                        .content("""
                                {"cancelReason":"환불"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRefundsByOwningStudentSucceeds() throws Exception {
        TuitionBill bill = tuitionBillRepository.save(
                new TuitionBill(60L, 1L, BigDecimal.valueOf(1_000_000), LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L));
        when(academicClient.findStudentByUserId(eq(2L))).thenReturn(new AcademicStudentResponse(60L, 2L));

        mockMvc.perform(get("/api/payment/refunds")
                        .param("tuitionBillId", String.valueOf(bill.getId()))
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "2")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());
    }
}

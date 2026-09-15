package com.msa4lmsv2payment.domain.installment;

import com.msa4lmsv2payment.domain.installment.entity.InstallmentPlan;
import com.msa4lmsv2payment.domain.installment.repository.InstallmentPlanRepository;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-151 - 분할납부 신청 심사(승인/반려) API가 ADMIN 전용임을 실제 SecurityFilterChain으로 검증한다.
 * Gateway가 검증 후 전달하는 X-User-Id/X-User-Role 헤더를 직접 흉내낸다(RefundSecurityIntegrationTest와 동일 패턴).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstallmentPlanSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;

    @Autowired
    private TuitionBillRepository tuitionBillRepository;

    private Long saveTuitionBill() {
        return tuitionBillRepository.save(new TuitionBill(90L, 1L, BigDecimal.valueOf(1_000_000),
                LocalDate.now().plusDays(30), TuitionBillStatus.UNPAID, 1L)).getId();
    }

    @Test
    void 미리보기는_인증이_필요하다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/installment-plans/preview")
                .contentType("application/json").content("{\"tuitionBillId\":1,\"totalRounds\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 교수는_분할납부_미리보기를_할수없다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/installment-plans/preview")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "3")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "PROFESSOR")
                .contentType("application/json").content("{\"tuitionBillId\":1,\"totalRounds\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 미리보기_잘못된_회차는_400이다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/installment-plans/preview")
                .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1")
                .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                .contentType("application/json").content("{\"tuitionBillId\":1,\"totalRounds\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증_헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(patch("/api/payment/installment-plans/1/review")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void STUDENT는_심사할_수_없다() throws Exception {
        InstallmentPlan plan = installmentPlanRepository.save(new InstallmentPlan(saveTuitionBill(), 2));

        mockMvc.perform(patch("/api/payment/installment-plans/" + plan.getId() + "/review")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN은_심사할_수_있다() throws Exception {
        InstallmentPlan plan = installmentPlanRepository.save(new InstallmentPlan(saveTuitionBill(), 2));

        mockMvc.perform(patch("/api/payment/installment-plans/" + plan.getId() + "/review")
                        .header(GatewayContextAuthenticationFilter.USER_ID_HEADER, "2")
                        .header(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "ADMIN")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isOk());
    }
}

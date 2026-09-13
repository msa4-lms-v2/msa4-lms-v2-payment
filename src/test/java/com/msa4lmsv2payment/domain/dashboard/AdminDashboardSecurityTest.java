package com.msa4lmsv2payment.domain.dashboard;

import com.msa4lmsv2payment.global.security.*;
import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(AdminDashboardSecurityTest.Config.class)
class AdminDashboardSecurityTest {
    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, GatewayContextVerifier.class, GatewayContextAuthenticationFilter.class, GatewayAuthenticationEntryPoint.class, GatewayAccessDeniedHandler.class, AdminDashboardController.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AdminDashboardService service() { return mock(AdminDashboardService.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired AdminDashboardService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(service.getDashboard()).thenReturn(new AdminDashboardResponse(null, new AdminDashboardResponse.Summary(0,0), java.util.List.of(), null));
    }
    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/payment/admin/dashboard")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"STUDENT","PROFESSOR"})
    void otherRolesAreForbidden(String role) throws Exception {
        mvc.perform(get("/api/payment/admin/dashboard").header("X-User-Id","1").header("X-User-Role",role))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminGetsFrontendContract() throws Exception {
        mvc.perform(get("/api/payment/admin/dashboard").header("X-User-Id","1").header("X-User-Role","ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data.summary.installmentPending").value(0))
                .andExpect(jsonPath("$.data.tasks").isArray());
        verify(service).getDashboard();
    }
}

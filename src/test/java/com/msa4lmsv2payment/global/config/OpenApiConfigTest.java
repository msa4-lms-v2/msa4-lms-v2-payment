package com.msa4lmsv2payment.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 API(Gateway 인증이 필요 없는 오퍼레이션)에는 Swagger 문서에도 Gateway 인증 요건과
 * 401/403 예시가 붙지 않아야 한다. 반대로 일반 API는 그대로 붙어야 한다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.api-docs.path=/api-docs"})
class OpenApiConfigTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 공개_API는_보안요건과_게이트웨이_인증_예시가_없다() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/verify'].get.security").isArray())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/verify'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/verify'].get.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/verify'].get.responses.403").doesNotExist());
    }

    @Test
    void 일반_API는_게이트웨이_인증_401_403_예시가_그대로_있다() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/{documentId}/revoke'].patch.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/payment/certificates/{documentId}/revoke'].patch.responses.403").exists());
    }
}

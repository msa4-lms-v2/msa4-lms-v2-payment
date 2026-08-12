package com.msa4lmsv2payment.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("stub")
class OpenApiRuntimeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatedOpenApiContainsDetailedPaymentAndRefundContracts() throws Exception {
        String paymentPath = "$['paths']['/api/payment/pg-requests']['post']";
        String refundPath = "$['paths']['/api/payment/refunds/virtual-account-requests']['post']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(paymentPath).exists())
                .andExpect(jsonPath(refundPath).exists())
                .andExpect(jsonPath("$['paths']['/api/payments/pg-requests']").doesNotExist())
                .andExpect(jsonPath(paymentPath + "['description']")
                        .value(containsString("SUCCEEDED는 종결 상태")))
                .andExpect(jsonPath(paymentPath + "['parameters'][?(@.name == 'Idempotency-Key')]['schema']['minLength']")
                        .value(1))
                .andExpect(jsonPath(paymentPath + "['parameters'][?(@.name == 'Idempotency-Key')]['schema']['maxLength']")
                        .value(100))
                .andExpect(jsonPath(paymentPath + "['responses']['409']['$ref']")
                        .value(OpenApiConfig.DUPLICATE_RESPONSE_REF))
                .andExpect(jsonPath(paymentPath + "['responses']['503']['$ref']")
                        .value(OpenApiConfig.DEPENDENCY_UNAVAILABLE_RESPONSE_REF))
                .andExpect(jsonPath(refundPath + "['responses']['201']['content']['application/json']['examples']['환불 연결 성공']")
                        .exists())
                .andExpect(jsonPath("$['components']['responses']['InvalidParameter']['content']['application/json']['example']['code']")
                        .value("E21"))
                .andExpect(jsonPath("$['components']['responses']['DuplicateData']['content']['application/json']['example']['message']")
                        .value("DUPLICATE_DATA"))
                .andExpect(jsonPath("$['components']['securitySchemes']['gatewayUserId']['name']")
                        .value("X-User-Id"))
                .andExpect(jsonPath("$['components']['securitySchemes']['gatewayUserRole']['name']")
                        .value("X-User-Role"));
    }
}

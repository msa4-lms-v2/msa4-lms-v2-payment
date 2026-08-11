package com.msa4lmsv2payment.global.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void Gateway_사용자_컨텍스트_헤더를_AND_인증_계약으로_등록한다() {
        var openApi = config.openApi();

        assertThat(openApi.getComponents().getSecuritySchemes())
                .containsOnlyKeys(
                        OpenApiConfig.GATEWAY_USER_ID,
                        OpenApiConfig.GATEWAY_USER_ROLE,
                        OpenApiConfig.GATEWAY_TIMESTAMP,
                        OpenApiConfig.GATEWAY_SIGNATURE)
                .doesNotContainKey("bearerAuth");
        assertThat(openApi.getSecurity()).singleElement().satisfies(requirement ->
                assertThat(requirement).containsOnlyKeys(
                        OpenApiConfig.GATEWAY_USER_ID,
                        OpenApiConfig.GATEWAY_USER_ROLE,
                        OpenApiConfig.GATEWAY_TIMESTAMP,
                        OpenApiConfig.GATEWAY_SIGNATURE));
    }

    @Test
    void 모든_API에_Gateway_인증과_권한_오류를_문서화한다() {
        Operation operation = new Operation().responses(new ApiResponses());

        config.gatewayErrorResponseCustomizer().customize(operation, null);

        assertThat(operation.getResponses().get("401").get$ref())
                .isEqualTo("#/components/responses/GatewayAuthenticationRequired");
        assertThat(operation.getResponses().get("403").get$ref())
                .isEqualTo("#/components/responses/GatewayAccessDenied");
    }
}

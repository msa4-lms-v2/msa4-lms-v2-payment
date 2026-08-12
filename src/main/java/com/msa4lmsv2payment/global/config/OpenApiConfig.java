package com.msa4lmsv2payment.global.config;

import com.msa4lmsv2payment.global.response.GlobalRes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String GATEWAY_USER_ID = "gatewayUserId";
    static final String GATEWAY_USER_ROLE = "gatewayUserRole";

    private static final String AUTHENTICATION_REQUIRED_RESPONSE = "GatewayAuthenticationRequired";
    private static final String ACCESS_DENIED_RESPONSE = "GatewayAccessDenied";

    @Bean
    public OpenAPI openApi() {
        SecurityRequirement gatewayContext = new SecurityRequirement()
                .addList(GATEWAY_USER_ID)
                .addList(GATEWAY_USER_ROLE);

        return new OpenAPI()
                .info(new Info()
                        .title("msa4-lms-v2-payment API")
                        .description("""
                                등록금·결제·환불·증명서 - Payment·문서 서비스

                                외부 클라이언트가 직접 호출하는 서비스가 아니다. SCG가 JWT를 검증한 뒤 사용자 컨텍스트(X-User-Id/X-User-Role)를 전달한다.
                                이 서비스는 인프라 단에서 Gateway 외의 접근이 차단된다는 전제로, 두 헤더 값을 서명 없이 그대로 신뢰한다.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_USER_ID, headerScheme(
                                "X-User-Id",
                                "SCG가 인증한 사용자의 양수 Long 식별자. 예: 1"))
                        .addSecuritySchemes(GATEWAY_USER_ROLE, headerScheme(
                                "X-User-Role",
                                "SCG가 인증한 역할. 허용값: STUDENT, PROFESSOR, ADMIN, SYSTEM"))
                        .addResponses(AUTHENTICATION_REQUIRED_RESPONSE, new ApiResponse()
                                .description("Gateway 사용자 컨텍스트 헤더 누락 또는 형식 오류")
                                .content(errorContent("E02", "인증이 필요합니다.")))
                        .addResponses(ACCESS_DENIED_RESPONSE, new ApiResponse()
                                .description("인증된 사용자에게 요청 기능의 역할 또는 소유권이 없음")
                                .content(errorContent("E03", "접근 권한이 없습니다."))))
                .addSecurityItem(gatewayContext);
    }

    @Bean
    public OperationCustomizer gatewayErrorResponseCustomizer() {
        return (operation, handlerMethod) -> addGatewayErrorResponses(operation);
    }

    private Operation addGatewayErrorResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.putIfAbsent("401", new ApiResponse()
                .$ref("#/components/responses/" + AUTHENTICATION_REQUIRED_RESPONSE));
        responses.putIfAbsent("403", new ApiResponse()
                .$ref("#/components/responses/" + ACCESS_DENIED_RESPONSE));
        return operation;
    }

    private Content errorContent(String code, String message) {
        return new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new io.swagger.v3.oas.models.media.MediaType()
                        .example(new GlobalRes<Void>(code, message, null)));
    }

    private SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}

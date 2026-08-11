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
    static final String GATEWAY_TIMESTAMP = "gatewayTimestamp";
    static final String GATEWAY_SIGNATURE = "gatewaySignature";

    private static final String AUTHENTICATION_REQUIRED_RESPONSE = "GatewayAuthenticationRequired";
    private static final String ACCESS_DENIED_RESPONSE = "GatewayAccessDenied";

    @Bean
    public OpenAPI openApi() {
        SecurityRequirement gatewayContext = new SecurityRequirement()
                .addList(GATEWAY_USER_ID)
                .addList(GATEWAY_USER_ROLE)
                .addList(GATEWAY_TIMESTAMP)
                .addList(GATEWAY_SIGNATURE);

        return new OpenAPI()
                .info(new Info()
                        .title("msa4-lms-v2-payment API")
                        .description("""
                                등록금·결제·환불·증명서 - Payment·문서 서비스

                                외부 클라이언트가 직접 호출하는 서비스가 아니다. SCG가 JWT를 검증한 뒤 아래 사용자 컨텍스트를 HMAC 서명해 전달한다.
                                Swagger UI에서 호출하려면 SCG와 같은 방식으로 미리 생성한 네 헤더 값을 입력해야 하며, Gateway 비밀키를 브라우저에 입력하거나 노출하지 않는다.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_USER_ID, headerScheme(
                                "X-User-Id",
                                "SCG가 인증한 사용자의 양수 Long 식별자. 예: 1"))
                        .addSecuritySchemes(GATEWAY_USER_ROLE, headerScheme(
                                "X-User-Role",
                                "SCG가 인증한 역할. 허용값: STUDENT, PROFESSOR, ADMIN, SYSTEM"))
                        .addSecuritySchemes(GATEWAY_TIMESTAMP, headerScheme(
                                "X-Gateway-Timestamp",
                                "서명 생성 시각의 Unix epoch seconds. 서버 허용 오차 기본값은 ±2분이다."))
                        .addSecuritySchemes(GATEWAY_SIGNATURE, headerScheme(
                                "X-Gateway-Signature",
                                """
                                        Gateway 전용 비밀키로 만든 HMAC-SHA256 서명의 Base64 URL-safe(no padding) 값.
                                        정규 문자열: {userId}\\n{role}\\n{timestamp}\\n{HTTP_METHOD}\\n{requestURI}
                                        예: 1\\nSTUDENT\\n1786406400\\nGET\\n/api/payments/student-tuition
                                        """))
                        .addResponses(AUTHENTICATION_REQUIRED_RESPONSE, new ApiResponse()
                                .description("Gateway 사용자 컨텍스트 헤더 누락, 형식 오류, 만료 또는 서명 불일치")
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

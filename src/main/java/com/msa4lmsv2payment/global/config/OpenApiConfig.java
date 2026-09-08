package com.msa4lmsv2payment.global.config;

import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
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
    private static final String INVALID_TOKEN_RESPONSE = "InvalidToken";
    private static final String SYSTEM_ERROR_RESPONSE = "SystemError";

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

                                모든 업무 API의 기준 경로는 /api/payment/** 이다.
                                외부 클라이언트가 직접 호출하는 서비스가 아니다. SCG가 JWT를 검증한 뒤 사용자 컨텍스트(X-User-Id/X-User-Role)를 전달한다.
                                이 서비스는 인프라 단에서 Gateway 외의 접근이 차단된다는 전제로, 두 헤더 값을 서명 없이 그대로 신뢰한다.

                                응답은 GlobalResponseDTO(code, message, data) 형식이다. 성공은 00/SUCCESS, 실패는 EXX 코드와 enum 메시지를 사용한다.
                                Idempotency-Key가 필요한 API는 1~100자를 받으며, 완료된 동일 요청은 저장된 응답을 재생하고 하위 업무나 PG를 다시 호출하지 않는다.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(GATEWAY_USER_ID, headerScheme(
                                "X-User-Id",
                                "SCG가 인증한 사용자의 양수 Long 식별자. 예: 1"))
                        .addSecuritySchemes(GATEWAY_USER_ROLE, headerScheme(
                                "X-User-Role",
                                "SCG가 인증한 사용자 역할. 허용값: STUDENT, PROFESSOR, ADMIN"))
                        .addResponses(AUTHENTICATION_REQUIRED_RESPONSE, new ApiResponse()
                                .description("Gateway 사용자 컨텍스트 헤더 누락 또는 형식 오류")
                                .content(errorContent("E02", "UNAUTHENTICATED")))
                        .addResponses(ACCESS_DENIED_RESPONSE, new ApiResponse()
                                .description("인증된 사용자에게 요청 기능의 역할 또는 소유권이 없음")
                                .content(errorContent("E03", "ACCESS_DENIED")))
                        .addResponses(INVALID_TOKEN_RESPONSE, errorResponse(
                                "Gateway가 전달받은 토큰이 유효하지 않음", "E04", "INVALID_TOKEN"))
                        .addResponses(SYSTEM_ERROR_RESPONSE, errorResponse(
                                "처리되지 않은 서버 오류", "E99", "SYSTEM_ERROR")))
                .addSecurityItem(gatewayContext);
    }

    @Bean
    public OperationCustomizer gatewayErrorResponseCustomizer() {
        return (operation, handlerMethod) -> addGatewayErrorResponses(operation);
    }

    // 메서드에 @SecurityRequirements(value = {})를 붙이면 springdoc이 operation.security를 빈 리스트로 설정해
    // OpenAPI 전역 기본 보안요건(gatewayContext)을 이 오퍼레이션에서 제외한다. 그렇게 명시적으로 공개 API로 표시된
    // 오퍼레이션에는 Gateway 인증(401)·권한(403) 에러 예시를 붙이지 않는다 - 실제로 X-User-Id/Role 없이 호출되기 때문이다.
    // 500 시스템 오류는 인증 여부와 무관하게 발생할 수 있어 모든 오퍼레이션에 그대로 남긴다.
    private Operation addGatewayErrorResponses(Operation operation) {
        boolean isPublic = operation.getSecurity() != null && operation.getSecurity().isEmpty();

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        if (!isPublic) {
            responses.putIfAbsent("401", new ApiResponse()
                    .$ref("#/components/responses/" + AUTHENTICATION_REQUIRED_RESPONSE));
            responses.putIfAbsent("403", new ApiResponse()
                    .$ref("#/components/responses/" + ACCESS_DENIED_RESPONSE));
        }
        responses.putIfAbsent("500", new ApiResponse()
                .$ref("#/components/responses/" + SYSTEM_ERROR_RESPONSE));
        return operation;
    }

    private ApiResponse errorResponse(String description, String code, String message) {
        return new ApiResponse()
                .description(description)
                .content(errorContent(code, message));
    }

    private Content errorContent(String code, String message) {
        return new Content().addMediaType(
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new io.swagger.v3.oas.models.media.MediaType()
                        .example(new GlobalResponseDTO<Void>(code, message, null)));
    }

    private SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}

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
    private static final String INVALID_TOKEN_RESPONSE = "InvalidToken";
    private static final String NOT_FOUND_RESPONSE = "NotFoundData";
    private static final String DUPLICATE_RESPONSE = "DuplicateData";
    private static final String INVALID_PARAMETER_RESPONSE = "InvalidParameter";
    private static final String DEPENDENCY_UNAVAILABLE_RESPONSE = "DependencyUnavailable";
    private static final String DEPENDENCY_TIMEOUT_RESPONSE = "DependencyTimeout";
    private static final String CIRCUIT_OPEN_RESPONSE = "CircuitOpen";
    private static final String SERVICE_RECOVERING_RESPONSE = "ServiceRecovering";
    private static final String MANUAL_REVIEW_REQUIRED_RESPONSE = "ManualReviewRequired";
    private static final String SYSTEM_ERROR_RESPONSE = "SystemError";

    public static final String NOT_FOUND_RESPONSE_REF = "#/components/responses/" + NOT_FOUND_RESPONSE;
    public static final String DUPLICATE_RESPONSE_REF = "#/components/responses/" + DUPLICATE_RESPONSE;
    public static final String INVALID_PARAMETER_RESPONSE_REF = "#/components/responses/" + INVALID_PARAMETER_RESPONSE;
    public static final String DEPENDENCY_UNAVAILABLE_RESPONSE_REF = "#/components/responses/" + DEPENDENCY_UNAVAILABLE_RESPONSE;

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

                                응답은 GlobalRes(code, message, data) 형식이다. 성공은 00/SUCCESS, 실패는 EXX 코드와 enum 메시지를 사용한다.
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
                        .addResponses(NOT_FOUND_RESPONSE, errorResponse(
                                "요청한 업무 데이터를 찾을 수 없음", "E10", "NOT_FOUND_DATA"))
                        .addResponses(DUPLICATE_RESPONSE, errorResponse(
                                "멱등 키 재사용 또는 결제 결과 대조 충돌", "E11", "DUPLICATE_DATA"))
                        .addResponses(INVALID_PARAMETER_RESPONSE, errorResponse(
                                "요청값 검증 실패 또는 허용되지 않는 상태 전이", "E21", "INVALID_PARAMETER"))
                        .addResponses(DEPENDENCY_UNAVAILABLE_RESPONSE, errorResponse(
                                "Academic 또는 토스페이먼츠 의존 서비스 사용 불가", "E90", "DEPENDENCY_UNAVAILABLE"))
                        .addResponses(DEPENDENCY_TIMEOUT_RESPONSE, errorResponse(
                                "의존 서비스 응답 시간 초과", "E91", "DEPENDENCY_TIMEOUT"))
                        .addResponses(CIRCUIT_OPEN_RESPONSE, errorResponse(
                                "의존 서비스 회로 차단기가 열려 호출을 빠르게 거부함", "E92", "CIRCUIT_OPEN"))
                        .addResponses(SERVICE_RECOVERING_RESPONSE, errorResponse(
                                "의존 서비스 복구 확인 중", "E93", "SERVICE_RECOVERING"))
                        .addResponses(MANUAL_REVIEW_REQUIRED_RESPONSE, errorResponse(
                                "자동 처리가 불가능해 관리자 확인 필요", "E94", "MANUAL_REVIEW_REQUIRED"))
                        .addResponses(SYSTEM_ERROR_RESPONSE, errorResponse(
                                "처리되지 않은 서버 오류", "E99", "SYSTEM_ERROR")))
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

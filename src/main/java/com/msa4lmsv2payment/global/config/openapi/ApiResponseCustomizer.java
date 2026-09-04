package com.msa4lmsv2payment.global.config.openapi;

import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @CustomApiResponse(value = {CustomResponseCode...})가 붙은 메서드에 그 코드들의 예시 응답을 채워 넣는다.
 * 컨트롤러마다 같은 에러 설명을 반복해서 적지 않고, CustomResponseCode 하나만 고쳐도 모든 Swagger 문서에 반영된다.
 */
@Component
public class ApiResponseCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        CustomApiResponse annotation = handlerMethod.getMethodAnnotation(CustomApiResponse.class);
        if (annotation == null) {
            return operation;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        Map<Integer, List<CustomResponseCode>> byHttpStatus = new LinkedHashMap<>();
        for (CustomResponseCode code : annotation.value()) {
            byHttpStatus.computeIfAbsent(code.getHttpStatus().value(), key -> new ArrayList<>()).add(code);
        }

        for (Map.Entry<Integer, List<CustomResponseCode>> entry : byHttpStatus.entrySet()) {
            String status = String.valueOf(entry.getKey());
            ApiResponse existing = responses.get(status);

            ApiResponse target = existing != null ? existing : new ApiResponse().description("에러 응답");
            Content content = target.getContent();
            if (content == null) {
                content = new Content();
                target.setContent(content);
            }
            MediaType mediaType = content.get("application/json");
            if (mediaType == null) {
                mediaType = new MediaType();
                content.addMediaType("application/json", mediaType);
            }
            for (CustomResponseCode code : entry.getValue()) {
                mediaType.addExamples(code.name(),
                        new Example().value(new GlobalResponseDTO<Void>(code.getCode(), code.name(), null)));
            }

            responses.addApiResponse(status, target);
        }

        return operation;
    }
}

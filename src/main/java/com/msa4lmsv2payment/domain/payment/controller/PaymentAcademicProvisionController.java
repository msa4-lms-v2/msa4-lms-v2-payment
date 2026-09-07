package com.msa4lmsv2payment.domain.payment.controller;

import com.msa4lmsv2payment.domain.payment.response.AcademicTuitionStatusResponseDTO;
import com.msa4lmsv2payment.domain.payment.service.PaymentService;
import com.msa4lmsv2payment.global.config.openapi.CustomApiResponse;
import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Academic이 호출하는 서비스 간 조회 전용 API(SCRUM-176). 사용자 요청이 아니라 시스템 요청이라
 * X-User-Id/X-User-Role을 검증하지 않는다 - SecurityConfig에서 permitAll 처리하고 실제 보호는
 * SCG Internal listener + NetworkPolicy가 담당한다(docs-v2/MSA-LMS_INTEGRATION.md).
 */
@Tag(name = "Payment", description = "Academic 제공 API")
@RestController
@RequiredArgsConstructor
public class PaymentAcademicProvisionController {

    private final PaymentService paymentService;

    @Operation(summary = "학생·학기별 등록금 납부 상태 조회(Academic 전용)",
            description = "Academic이 수강신청 자격 확인 등에 쓰는 시스템 간 조회다. 해당 학생·학기의 등록금 고지가 아직 없으면 404를 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @CustomApiResponse({CustomResponseCode.NOT_FOUND_DATA})
    @GetMapping("/api/payment/academic-provision/students/{studentId}/semesters/{semesterId}/tuition-status")
    public GlobalResponseDTO<AcademicTuitionStatusResponseDTO> getTuitionStatusForAcademic(
            @PathVariable Long studentId,
            @PathVariable Long semesterId
    ) {
        return GlobalResponseDTO.success(paymentService.getTuitionStatusForAcademic(studentId, semesterId));
    }
}

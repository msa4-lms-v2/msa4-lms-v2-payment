package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.request.DocumentRevokeRequestDTO;
import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.CertificateVerificationResponseDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.document.service.DocumentService;
import com.msa4lmsv2payment.global.config.openapi.CustomApiResponse;
import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "납부 확인서 등 증명서 발급·진위확인·폐기")
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "납부 확인서", description = "해당 고지에 SUCCEEDED 결제 이력이 있어야만 발급된다. 발급 시 검증 토큰과 그 해시를 함께 저장한다. STUDENT 본인 / ADMIN 관리 범위.")
    @ApiResponse(responseCode = "201", description = "발급 성공")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED, CustomResponseCode.NOT_FOUND_DATA})
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payment/payment-receipts")
    public GlobalResponseDTO<DocumentResponseDTO> issuePaymentReceipt(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentReceiptRequestDTO request
    ) {
        return GlobalResponseDTO.success(documentService.issuePaymentReceipt(currentUser, request));
    }

    @Operation(summary = "증명서 진위확인", description = """
            제3자(회사·기관)가 로그인 없이 QR/링크의 검증 토큰만으로 증명서 유효 여부를 조회한다.
            qrHash를 함께 넘기면 토큰의 SHA-256 해시와 대조해 위조 여부까지 확인한다(생략 가능).
            학생·교수 식별 정보는 응답에 포함하지 않는다. 인증 없이 호출 가능한 공개 API.
            """)
    @ApiResponse(responseCode = "200", description = "조회 성공(VALID 또는 REVOKED)")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.NOT_FOUND_DATA})
    @SecurityRequirements
    @GetMapping("/api/payment/certificates/verify")
    public GlobalResponseDTO<CertificateVerificationResponseDTO> verifyCertificate(
            @RequestParam String token,
            @RequestParam(required = false) String qrHash,
            HttpServletRequest httpRequest
    ) {
        return GlobalResponseDTO.success(documentService.verifyCertificate(token, qrHash, resolveClientIp(httpRequest)));
    }

    @Operation(summary = "증명서 폐기", description = "ADMIN이 이미 발급된 증명서를 폐기한다. 이후 진위확인 조회는 REVOKED로 응답된다.")
    @ApiResponse(responseCode = "200", description = "폐기 성공")
    @CustomApiResponse({CustomResponseCode.INVALID_PARAMETER, CustomResponseCode.ACCESS_DENIED,
            CustomResponseCode.NOT_FOUND_DATA, CustomResponseCode.DUPLICATE_DATA})
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/payment/certificates/{documentId}/revoke")
    public GlobalResponseDTO<DocumentResponseDTO> revokeDocument(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long documentId,
            @RequestBody @Valid DocumentRevokeRequestDTO request
    ) {
        return GlobalResponseDTO.success(documentService.revokeDocument(admin, documentId, request));
    }

    // SCG의 RateLimitFilter와 동일한 이유로 X-Forwarded-For는 신뢰하지 않는다 - 신뢰 프록시 목록이 아직 없어
    // 클라이언트가 위조한 값을 그대로 덮어쓰는 앞단 장비가 있다고 보장할 수 없다. 이 값은 SCG 경유 시 SCG 파드 주소가 된다.
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}

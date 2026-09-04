package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.document.service.DocumentService;
import com.msa4lmsv2payment.global.config.openapi.CustomApiResponse;
import com.msa4lmsv2payment.global.response.constant.CustomResponseCode;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "납부 확인서 등 증명서 발급")
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
}

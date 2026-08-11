package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.document.service.DocumentService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "납부 이력 없음"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지")
    })
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payments/payment-receipts")
    public GlobalRes<DocumentResponseDTO> issuePaymentReceipt(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid PaymentReceiptRequestDTO request
    ) {
        return GlobalRes.success(documentService.issuePaymentReceipt(currentUser, request));
    }
}

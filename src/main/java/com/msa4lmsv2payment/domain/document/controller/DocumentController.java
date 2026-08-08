package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.request.PaymentReceiptRequestDTO;
import com.msa4lmsv2payment.domain.document.response.DocumentResponseDTO;
import com.msa4lmsv2payment.domain.document.service.DocumentService;
import com.msa4lmsv2payment.global.response.GlobalRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

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

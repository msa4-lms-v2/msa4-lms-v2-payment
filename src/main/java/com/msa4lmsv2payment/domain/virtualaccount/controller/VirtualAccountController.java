package com.msa4lmsv2payment.domain.virtualaccount.controller;

import com.msa4lmsv2payment.domain.virtualaccount.request.VirtualAccountIssueRequestDTO;
import com.msa4lmsv2payment.domain.virtualaccount.response.VirtualAccountResponseDTO;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
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
public class VirtualAccountController {

    private final VirtualAccountService virtualAccountService;

    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/payments/virtual-accounts")
    public GlobalRes<VirtualAccountResponseDTO> issueVirtualAccount(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody @Valid VirtualAccountIssueRequestDTO request
    ) {
        return GlobalRes.success(virtualAccountService.issueVirtualAccount(currentUser, request));
    }
}

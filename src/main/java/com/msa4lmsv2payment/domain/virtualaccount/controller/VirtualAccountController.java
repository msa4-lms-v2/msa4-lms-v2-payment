package com.msa4lmsv2payment.domain.virtualaccount.controller;

import com.msa4lmsv2payment.domain.virtualaccount.request.VirtualAccountIssueRequestDTO;
import com.msa4lmsv2payment.domain.virtualaccount.response.VirtualAccountResponseDTO;
import com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountService;
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

@Tag(name = "Payment", description = "가상계좌 발급")
@RestController
@RequiredArgsConstructor
public class VirtualAccountController {

    private final VirtualAccountService virtualAccountService;

    @Operation(summary = "가상계좌 발급", description = "토스페이먼츠에 가상계좌 발급을 요청하고 결과를 저장한다. STUDENT 본인 결제·문서 / ADMIN 관리 범위.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급 성공"),
            @ApiResponse(responseCode = "403", description = "본인 고지가 아님"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 고지"),
            @ApiResponse(responseCode = "503", description = "토스페이먼츠 연결 실패(TOSS_SECRET_KEY 미설정 포함)")
    })
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

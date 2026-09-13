package com.msa4lmsv2payment.domain.dashboard;

import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin dashboard", description = "관리자 누적 대기 업무 및 현재 학기 등록금 통계")
@RestController
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService service;

    @Operation(summary = "관리자 등록금 대시보드 조회")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/payment/admin/dashboard")
    public GlobalResponseDTO<AdminDashboardResponse> getDashboard() {
        return GlobalResponseDTO.success(service.getDashboard());
    }
}

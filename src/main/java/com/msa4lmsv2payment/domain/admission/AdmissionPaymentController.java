package com.msa4lmsv2payment.domain.admission;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/payment/admission-candidates/{candidateId}/tuition")
public class AdmissionPaymentController {
    private final AdmissionPaymentService service;
    @GetMapping public GlobalResponseDTO<AdmissionPaymentService.Detail> get(@PathVariable Long candidateId){return GlobalResponseDTO.success(service.get(candidateId));}
    @PostMapping public GlobalResponseDTO<AdmissionPaymentService.Detail> issue(@PathVariable Long candidateId,@Valid @RequestBody AdmissionBillRequest request,@AuthenticationPrincipal CurrentUser user){return GlobalResponseDTO.success(service.issue(candidateId,request,user));}
    @PostMapping("/reissue")
    public GlobalResponseDTO<AdmissionPaymentService.Detail> reissue(@PathVariable Long candidateId,
            @Valid @RequestBody AdmissionReissueRequest request, @AuthenticationPrincipal CurrentUser user) {
        return GlobalResponseDTO.success(service.reissue(candidateId, request, user));
    }
}

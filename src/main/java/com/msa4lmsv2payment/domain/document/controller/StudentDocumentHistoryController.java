package com.msa4lmsv2payment.domain.document.controller;

import com.msa4lmsv2payment.domain.document.repository.DocumentRepository;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.document.controller.ProfessorDocumentHistoryController.History;
import com.msa4lmsv2payment.domain.document.controller.ProfessorDocumentHistoryController.Item;
import com.msa4lmsv2payment.global.response.GlobalResponseDTO;
import com.msa4lmsv2payment.global.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class StudentDocumentHistoryController {
    private final DocumentRepository repository;
    private final TuitionBillService tuitionBillService;

    @GetMapping("/api/payment/students/me/certificates")
    @PreAuthorize("hasRole('STUDENT')")
    public GlobalResponseDTO<History> history(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "1") @Min(1) @Max(100000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Long studentId = tuitionBillService.resolveStudentId(user);
        var result = repository.findByStudentIdOrderByIssuedAtDescIdDesc(studentId, PageRequest.of(page - 1, size));
        return GlobalResponseDTO.success(new History(result.getContent().stream().map(Item::from).toList(),
                result.getTotalElements(), page, size, result.hasNext()));
    }
}

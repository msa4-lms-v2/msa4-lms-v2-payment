package com.msa4lmsv2payment.domain.scholarship.service;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import com.msa4lmsv2payment.domain.scholarship.repository.ScholarshipRepository;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.request.ScholarshipDiscountRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final TuitionBillService tuitionBillService;

    @Transactional
    public ScholarshipResponseDTO applyScholarshipDiscount(CurrentUser currentUser, ScholarshipDiscountRequestDTO request) {
        tuitionBillService.getTuitionBillOrThrow(request.tuitionBillId());

        Scholarship scholarship = new Scholarship(
                request.tuitionBillId(),
                request.type(),
                request.amount(),
                request.reason(),
                currentUser.id()
        );

        return ScholarshipResponseDTO.from(scholarshipRepository.save(scholarship));
    }

    public PaymentScholarshipAllocationResponseDTO calculateAllocation(CurrentUser currentUser, PaymentScholarshipAllocationRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        BigDecimal totalScholarshipAmount = scholarshipRepository.findByTuitionBillId(tuitionBill.getId()).stream()
                .map(Scholarship::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualPaymentAmount = tuitionBill.getBillingAmount().subtract(totalScholarshipAmount);

        return new PaymentScholarshipAllocationResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getBillingAmount(),
                totalScholarshipAmount,
                actualPaymentAmount
        );
    }
}

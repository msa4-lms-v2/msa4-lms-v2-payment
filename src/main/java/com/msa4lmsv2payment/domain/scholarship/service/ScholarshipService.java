package com.msa4lmsv2payment.domain.scholarship.service;

import com.msa4lmsv2payment.domain.scholarship.entity.Scholarship;
import com.msa4lmsv2payment.global.error.ScholarshipExceedsBillingAmountException;
import com.msa4lmsv2payment.domain.scholarship.repository.ScholarshipRepository;
import com.msa4lmsv2payment.domain.scholarship.request.PaymentScholarshipAllocationRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.request.ScholarshipDiscountRequestDTO;
import com.msa4lmsv2payment.domain.scholarship.response.MyScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.PaymentScholarshipAllocationResponseDTO;
import com.msa4lmsv2payment.domain.scholarship.response.ScholarshipResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final TuitionBillService tuitionBillService;

    // 5.1: 동시에 여러 장학금이 적용될 때 합계 초과를 놓치지 않도록 고지 행을 잠근 뒤 합계를 다시 계산한다.
    @Transactional
    public ScholarshipResponseDTO applyScholarshipDiscount(CurrentUser currentUser, ScholarshipDiscountRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getTuitionBillForUpdateOrThrow(request.tuitionBillId());

        BigDecimal existingTotal = scholarshipRepository.findByTuitionBillId(tuitionBill.getId()).stream()
                .map(Scholarship::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (existingTotal.add(request.amount()).compareTo(tuitionBill.getBillingAmount()) > 0) {
            throw new ScholarshipExceedsBillingAmountException(
                    "장학금 합계가 등록금 고지 금액을 초과할 수 없습니다.");
        }

        Scholarship scholarship = new Scholarship(
                request.tuitionBillId(),
                request.type(),
                request.amount(),
                request.reason(),
                currentUser.id()
        );

        return ScholarshipResponseDTO.from(scholarshipRepository.save(scholarship));
    }

    // 학생 · 장학금 수혜 내역 화면 - 본인의 모든 학기 장학금을 학기 ID와 함께 반환한다(Figma 요구: 학기 선택 시 수혜 종류·금액 표시).
    // getMyTuitionBills가 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<MyScholarshipResponseDTO> getMyScholarships(CurrentUser student) {
        List<TuitionBillResponseDTO> myBills = tuitionBillService.getMyTuitionBills(student);
        Map<Long, Long> semesterIdByTuitionBillId = myBills.stream()
                .collect(Collectors.toMap(TuitionBillResponseDTO::id, TuitionBillResponseDTO::semesterId));

        return scholarshipRepository.findByTuitionBillIdIn(myBills.stream().map(TuitionBillResponseDTO::id).toList()).stream()
                .map(scholarship -> MyScholarshipResponseDTO.from(scholarship, semesterIdByTuitionBillId.get(scholarship.getTuitionBillId())))
                .toList();
    }

    // 이미 소유권이 확인된 등록금 고지에 대해 순수 로컬 DB 합계만 구한다(Academic 호출 없음).
    public BigDecimal sumScholarshipAmount(Long tuitionBillId) {
        return scholarshipRepository.findByTuitionBillId(tuitionBillId).stream()
                .map(Scholarship::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PaymentScholarshipAllocationResponseDTO calculateAllocation(CurrentUser currentUser, PaymentScholarshipAllocationRequestDTO request) {
        TuitionBill tuitionBill = tuitionBillService.getOwnedTuitionBillOrThrow(currentUser, request.tuitionBillId());

        BigDecimal totalScholarshipAmount = scholarshipRepository.findByTuitionBillId(tuitionBill.getId()).stream()
                .map(Scholarship::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // applyScholarshipDiscount가 생성 시점에 합계 초과를 막지만, 그 방어를 우회한 데이터가 있을 경우의 최종 방어선.
        BigDecimal actualPaymentAmount = tuitionBill.getBillingAmount().subtract(totalScholarshipAmount).max(BigDecimal.ZERO);

        return new PaymentScholarshipAllocationResponseDTO(
                tuitionBill.getId(),
                tuitionBill.getBillingAmount(),
                totalScholarshipAmount,
                actualPaymentAmount
        );
    }
}

package com.msa4lmsv2payment.domain.tuitionrate.service;

import com.msa4lmsv2payment.domain.admission.AdmissionPaymentConflictException;
import com.msa4lmsv2payment.domain.tuitionrate.entity.DepartmentTuitionRate;
import com.msa4lmsv2payment.domain.tuitionrate.repository.DepartmentTuitionRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentTuitionRateService {
    private final DepartmentTuitionRateRepository rates;

    public DepartmentTuitionRate getRequired(Long departmentId, Long semesterId) {
        if (departmentId == null || semesterId == null) {
            throw new AdmissionPaymentConflictException("등록금 기준을 조회할 학과와 학기를 확인해 주세요.");
        }
        return rates.findByDepartmentIdAndSemesterId(departmentId, semesterId)
                .orElseThrow(() -> new AdmissionPaymentConflictException(
                        "해당 학과·학기의 등록금 기준 금액이 등록되지 않았습니다."));
    }

    public void validateDepartment(Long rateId, Long departmentId) {
        if (rateId == null) return; // 자동 책정 도입 전 고지는 발급 당시 금액을 유지한다.
        var rate = rates.findById(rateId)
                .orElseThrow(() -> new AdmissionPaymentConflictException("기존 고지의 등록금 기준을 확인할 수 없습니다."));
        if (!rate.getDepartmentId().equals(departmentId)) {
            throw new AdmissionPaymentConflictException("기존 고지의 학과와 입학 예정자의 학과가 일치하지 않습니다.");
        }
    }
}

package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.error.TuitionBillAccessDeniedException;
import com.msa4lmsv2payment.domain.tuitionbill.error.TuitionBillNotFoundException;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillQueryRepository;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionPaymentStatusResponseDTO;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.response.PageRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TuitionBillService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TuitionBillRepository tuitionBillRepository;
    private final TuitionBillQueryRepository tuitionBillQueryRepository;
    private final AcademicClient academicClient;

    public PageRes<TuitionBillResponseDTO> getAdminTuitionBills(TuitionBillStatus status, int page, int size) {
        int clampedSize = Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * clampedSize;

        List<TuitionBillResponseDTO> items = tuitionBillQueryRepository.search(status, offset, clampedSize).stream()
                .map(TuitionBillResponseDTO::from)
                .toList();
        long totalCount = tuitionBillQueryRepository.count(status);
        boolean hasNext = (long) offset + items.size() < totalCount;

        return new PageRes<>(items, totalCount, safePage, clampedSize, hasNext);
    }

    public List<TuitionBillResponseDTO> getMyTuitionBills(CurrentUser currentUser) {
        Long studentId = resolveStudentId(currentUser);
        return tuitionBillRepository.findByStudentIdOrderByDueDateDesc(studentId).stream()
                .map(TuitionBillResponseDTO::from)
                .toList();
    }

    public TuitionPaymentStatusResponseDTO getTuitionPaymentStatus(CurrentUser currentUser, Long tuitionBillId) {
        TuitionBill tuitionBill = getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        return TuitionPaymentStatusResponseDTO.from(tuitionBill);
    }

    /**
     * ADMIN은 전체 고지에, STUDENT는 본인 고지에만 접근 가능하도록 검증한 뒤 엔티티를 반환한다.
     * 다른 도메인(scholarship 등)이 tuition_bill 소유권을 확인해야 할 때도 이 메서드를 거친다(B1번 패키지 경계).
     */
    public TuitionBill getOwnedTuitionBillOrThrow(CurrentUser currentUser, Long tuitionBillId) {
        TuitionBill tuitionBill = getTuitionBillOrThrow(tuitionBillId);

        if (!currentUser.isAdmin()) {
            Long studentId = resolveStudentId(currentUser);
            if (!tuitionBill.getStudentId().equals(studentId)) {
                throw new TuitionBillAccessDeniedException("본인의 등록금 고지만 조회할 수 있습니다.");
            }
        }

        return tuitionBill;
    }

    public TuitionBill getTuitionBillOrThrow(Long tuitionBillId) {
        return tuitionBillRepository.findById(tuitionBillId)
                .orElseThrow(() -> new TuitionBillNotFoundException("해당 등록금 고지를 찾을 수 없습니다."));
    }

    private Long resolveStudentId(CurrentUser currentUser) {
        return academicClient.findStudentByUserId(currentUser.id()).id();
    }
}

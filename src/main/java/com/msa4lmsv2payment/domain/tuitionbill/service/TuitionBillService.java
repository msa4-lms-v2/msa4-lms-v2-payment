package com.msa4lmsv2payment.domain.tuitionbill.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.global.error.TuitionBillAccessDeniedException;
import com.msa4lmsv2payment.global.error.TuitionBillNotFoundException;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillQueryRepository;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.request.TuitionBillCreateRequestDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionPaymentStatusResponseDTO;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.response.PageRes;
import com.msa4lmsv2payment.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final TuitionBillRecorder tuitionBillRecorder;

    // SCRUM-43: 관리자 등록금 고지
    // Academic 호출(academicClient) 동안 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행한다(B3번).
    // 저장과 감사 로그는 tuitionBillRecorder가 하나의 트랜잭션으로 묶는다(4.6).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TuitionBillResponseDTO createTuitionBill(CurrentUser admin, TuitionBillCreateRequestDTO request) {
        academicClient.findStudent(request.studentId());
        academicClient.findSemester(request.semesterId());

        TuitionBill tuitionBill = new TuitionBill(
                request.studentId(),
                request.semesterId(),
                request.billingAmount(),
                request.dueDate(),
                TuitionBillStatus.UNPAID,
                admin.id()
        );

        return TuitionBillResponseDTO.from(tuitionBillRecorder.saveWithAudit(admin.id(), tuitionBill));
    }

    // SCRUM-77: 학생 등록금 고지서 조회 (43이 만든 고지 단건을 학생이 확인)
    // getOwnedTuitionBillOrThrow가 STUDENT 호출 시 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TuitionBillResponseDTO getStudentTuitionBill(CurrentUser student, Long tuitionBillId) {
        return TuitionBillResponseDTO.from(getOwnedTuitionBillOrThrow(student, tuitionBillId));
    }

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

    // resolveStudentId가 Academic을 호출해 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<TuitionBillResponseDTO> getMyTuitionBills(CurrentUser currentUser) {
        Long studentId = resolveStudentId(currentUser);
        return tuitionBillRepository.findByStudentIdOrderByDueDateDesc(studentId).stream()
                .map(TuitionBillResponseDTO::from)
                .toList();
    }

    // getOwnedTuitionBillOrThrow가 STUDENT 호출 시 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TuitionPaymentStatusResponseDTO getTuitionPaymentStatus(CurrentUser currentUser, Long tuitionBillId) {
        TuitionBill tuitionBill = getOwnedTuitionBillOrThrow(currentUser, tuitionBillId);
        return TuitionPaymentStatusResponseDTO.from(tuitionBill);
    }

    /**
     * ADMIN은 전체 고지에, STUDENT는 본인 고지에만 접근 가능하도록 검증한 뒤 엔티티를 반환한다.
     * 다른 도메인(scholarship 등)이 tuition_bill 소유권을 확인해야 할 때도 이 메서드를 거친다(B1번 패키지 경계).
     * STUDENT 호출 시 Academic을 부를 수 있어 트랜잭션 밖에서 실행한다(B3번) - 다른 서비스가 빈 경계를 넘어(프록시를 거쳐)
     * 이 메서드를 직접 호출할 때는 호출부 자신의 propagation 설정과 무관하게 이 메서드 자신의 propagation이 적용된다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    /**
     * SCRUM-112 - 다른 도메인(payment 등)이 고지 상태를 바꿔야 할 때 이 공개 메서드를 거친다(B1번 패키지 경계).
     */
    @Transactional
    public void changeStatus(Long tuitionBillId, TuitionBillStatus status) {
        getTuitionBillOrThrow(tuitionBillId).changeStatus(status);
    }

    private Long resolveStudentId(CurrentUser currentUser) {
        return academicClient.findStudentByUserId(currentUser.id()).id();
    }
}

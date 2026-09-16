package com.msa4lmsv2payment.domain.admission;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.tuitionbill.response.TuitionBillResponseDTO;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.response.VirtualAccountResponseDTO;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.security.CurrentUser;
import com.msa4lmsv2payment.domain.tuitionrate.service.DepartmentTuitionRateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdmissionPaymentService {
    private final AdmissionAcademicClient academic;
    private final AcademicClient catalog;
    private final TuitionBillRepository bills;
    private final VirtualAccountRepository accounts;
    private final AdmissionPaymentRecorder recorder;
    private final TossPaymentsClient toss;
    private final DepartmentTuitionRateService tuitionRates;

    public record Quote(Long departmentId, Long semesterId, BigDecimal billingAmount) {}

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("관리자만 입학 등록금을 처리할 수 있습니다.");
        }
    }

    public Quote quote(Long candidateId, Long semesterId, CurrentUser admin) {
        requireAdmin(admin);
        var candidate = academic.get(candidateId);
        requireEligible(candidate);
        validateSemester(candidate, semesterId);
        var existing = bills.findByAdmissionCandidateId(candidateId);
        if (existing.isPresent()) {
            var bill = existing.get();
            if (!bill.getSemesterId().equals(semesterId)) {
                throw new AdmissionPaymentConflictException("이미 발급한 고지의 학기를 선택해 주세요.");
            }
            tuitionRates.validateDepartment(bill.getTuitionRateId(), candidate.departmentId());
            return new Quote(candidate.departmentId(), semesterId, bill.getBillingAmount());
        }
        var rate = tuitionRates.getRequired(candidate.departmentId(), semesterId);
        return new Quote(candidate.departmentId(), semesterId, rate.getAmount());
    }

    private void validateSemester(AdmissionAcademicClient.Candidate candidate, Long semesterId) {
        var semester = catalog.findSemester(semesterId);
        if (semester.startDate() == null || semester.startDate().getYear() != candidate.admissionYear()) {
            throw new AdmissionPaymentConflictException("입학 연도와 고지 학기가 일치해야 합니다.");
        }
    }

    public record Detail(TuitionBillResponseDTO bill, VirtualAccountResponseDTO virtualAccount,
                         String syncError, boolean syncComplete, String bankCode,
                         boolean canReissue, boolean reissuePending) {}

    public Detail get(Long candidateId) {
        var bill = bills.findByAdmissionCandidateId(candidateId).orElse(null);
        if (bill == null) return null;
        var current = accounts.findByOrderId(bill.currentAdmissionOrderId());
        var account = current.or(() -> accounts.findFirstByTuitionBillIdOrderByIdDesc(bill.getId())).orElse(null);
        boolean pending = account != null && !bill.currentAdmissionOrderId().equals(account.getOrderId());
        boolean canReissue = account != null && account.getStatus() != VirtualAccountStatus.DEPOSITED
                && (pending || !account.getExpiresAt().isAfter(LocalDateTime.now()))
                && bill.getStatus() != TuitionBillStatus.PAID && bill.getStatus() != TuitionBillStatus.PARTIAL
                && bill.getStudentId() == null;
        return new Detail(TuitionBillResponseDTO.from(bill), account == null ? null : VirtualAccountResponseDTO.from(account),
                bill.getAdmissionSyncError(), bill.isAdmissionSyncComplete(), bill.getAdmissionBankCode(), canReissue, pending);
    }

    private void requireEligible(AdmissionAcademicClient.Candidate candidate) {
        if (!"PENDING".equals(candidate.status()) || candidate.tuitionPaid()
                || candidate.studentId() != null || candidate.advisorProfessorId() == null) {
            throw new AdmissionPaymentConflictException("완납 전 등록 대기 대상과 지도교수를 확인하세요.");
        }
    }

    public Detail issue(Long candidateId, AdmissionBillRequest request, CurrentUser admin) {
        requireAdmin(admin);
        var candidate = academic.get(candidateId);
        requireEligible(candidate);
        var semester = catalog.findSemester(request.semesterId());
        if (semester.startDate() == null || semester.startDate().getYear() != candidate.admissionYear()) {
            throw new AdmissionPaymentConflictException("입학 연도와 고지 학기가 일치해야 합니다.");
        }
        var bill = recorder.reserve(candidateId, candidate.name(), admin.id(), candidate.departmentId(), request);
        var bound = academic.post(candidateId, "bill", bill.getId());
        if (!bound.name().equals(bill.getAdmissionCustomerName()) || bound.admissionYear() != semester.startDate().getYear()
                || !"PENDING".equals(bound.status()) || !bill.getId().equals(bound.tuitionBillId())
                || !Objects.equals(candidate.departmentId(), bound.departmentId())) {
            throw new AdmissionPaymentConflictException("고지 생성 중 입학 정보가 변경됐습니다. 관리자 확인이 필요합니다.");
        }
        issueCurrent(bill, admin.id());
        return get(candidateId);
    }

    public Detail reissue(Long candidateId, AdmissionReissueRequest request, CurrentUser admin) {
        requireAdmin(admin);
        requireEligible(academic.get(candidateId));
        var bill = bills.findByAdmissionCandidateId(candidateId)
                .orElseThrow(() -> new AdmissionPaymentConflictException("기존 고지가 없습니다."));
        var previous = accounts.findById(request.previousVirtualAccountId())
                .orElseThrow(() -> new AdmissionPaymentConflictException("이전 가상계좌가 없습니다."));
        if (bill.getStatus() == TuitionBillStatus.PAID || bill.getStatus() == TuitionBillStatus.PARTIAL || bill.getStudentId() != null
                || previous.getPaymentKey() == null) {
            throw new AdmissionPaymentConflictException("입금 상태 또는 기존 결제키를 확인한 후 재발급하세요.");
        }
        if (!bill.getId().equals(previous.getTuitionBillId())) {
            throw new AdmissionPaymentConflictException("입학 고지와 이전 계좌가 일치하지 않습니다.");
        }
        String nextOrder = "ADMISSION-" + bill.getId() + "-R" + previous.getId();
        if (!nextOrder.equals(bill.currentAdmissionOrderId())) {
            if (request.dueDate().isBefore(java.time.LocalDate.now())) {
                throw new AdmissionPaymentConflictException("새 납부 기한은 오늘 이후여야 합니다.");
            }
            if (!previous.getOrderId().equals(bill.currentAdmissionOrderId()) || previous.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new AdmissionPaymentConflictException("현재 만료 계좌만 재발급할 수 있습니다. 최신 정보를 다시 조회하세요.");
            }
            var pg = toss.getPaymentByOrderId(previous.getOrderId());
            if (pg == null || !previous.getOrderId().equals(pg.orderId()) || !previous.getPaymentKey().equals(pg.paymentKey())) {
                throw new AdmissionPaymentConflictException("이전 계좌의 결제사 정보를 확인할 수 없습니다.");
            }
            if ("WAITING_FOR_DEPOSIT".equals(pg.status())) {
                var cancelled = toss.cancelPayment(previous.getPaymentKey(), "입학 가상계좌 기한 만료 재발급", null, null,
                        "ADMISSION-EXPIRE-" + previous.getId());
                if (cancelled == null || !"CANCELED".equals(cancelled.status())) {
                    throw new AdmissionPaymentConflictException("이전 계좌의 결제사 취소를 확인한 후 다시 요청하세요.");
                }
            } else if (!Set.of("EXPIRED", "CANCELED", "ABORTED").contains(pg.status())) {
                throw new AdmissionPaymentConflictException("이전 계좌에 입금이 확인되거나 처리 중입니다. 재발급하지 말고 납부 상태를 확인하세요.");
            }
        }
        requireEligible(academic.get(candidateId));
        var reserved = recorder.prepareReissue(bill.getId(), request);
        issueCurrent(reserved, admin.id());
        return get(candidateId);
    }

    private void issueCurrent(TuitionBill bill, Long adminId) {
        String orderId = bill.currentAdmissionOrderId();
        if (accounts.findByOrderId(orderId).isPresent()) return;
        if (bill.getStatus() == TuitionBillStatus.PAID || bill.getStatus() == TuitionBillStatus.PARTIAL) {
            throw new AdmissionPaymentConflictException("입금 처리 중인 고지에는 새 계좌를 발급하지 않습니다.");
        }
        var response = toss.findVirtualAccountByOrderId(orderId);
        if (response == null) {
            if (bill.getDueDate().isBefore(java.time.LocalDate.now())) {
                throw new AdmissionPaymentConflictException("요청 기한이 지났고 결제사 발급 내역이 없습니다. 관리자 확인이 필요합니다.");
            }
            response = toss.issueVirtualAccount(orderId, "입학 등록금", bill.getBillingAmount(), bill.getAdmissionCustomerName(),
                    bill.getAdmissionBankCode(), bill.getDueDate().atTime(23, 59, 59).atOffset(ZoneOffset.ofHours(9)).toString());
        } else {
            var pg = toss.getPaymentByOrderId(orderId);
            if (pg == null || !orderId.equals(pg.orderId()) || response.paymentKey() == null || !response.paymentKey().equals(pg.paymentKey())
                    || pg.totalAmount() == null || bill.getBillingAmount().compareTo(BigDecimal.valueOf(pg.totalAmount())) != 0
                    || !("WAITING_FOR_DEPOSIT".equals(pg.status()) || "EXPIRED".equals(pg.status()) || pg.isDone())) {
                throw new AdmissionPaymentConflictException("기존 발급 계좌의 금액·상태를 확인해야 합니다. 새 계좌를 중복 발급하지 않습니다.");
            }
        }
        recorder.issued(bill.getId(), orderId, response, adminId);
    }
}

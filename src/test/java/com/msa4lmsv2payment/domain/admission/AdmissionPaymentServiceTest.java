package com.msa4lmsv2payment.domain.admission;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.client.AcademicClient;
import com.msa4lmsv2payment.global.client.AcademicSemesterResponse;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossVirtualAccountIssueResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdmissionPaymentServiceTest {
    private final AdmissionAcademicClient academic = mock(AdmissionAcademicClient.class);
    private final AcademicClient catalog = mock(AcademicClient.class);
    private final TuitionBillRepository bills = mock(TuitionBillRepository.class);
    private final VirtualAccountRepository accounts = mock(VirtualAccountRepository.class);
    private final AdmissionPaymentRecorder recorder = mock(AdmissionPaymentRecorder.class);
    private final TossPaymentsClient toss = mock(TossPaymentsClient.class);
    private final AdmissionPaymentService service = new AdmissionPaymentService(academic, catalog, bills, accounts, recorder, toss);
    private final AdmissionBillRequest request = new AdmissionBillRequest(1L, new BigDecimal("10000"), LocalDate.now().plusDays(7), "88");

    private void pending() {
        var candidate = new AdmissionAcademicClient.Candidate(7L, "학생", (short)2027, "PENDING", 100L, false, null, 3L);
        var bill = new TuitionBill(null, 1L, request.billingAmount(), request.dueDate(), TuitionBillStatus.UNPAID, 1L);
        bill.admission(7L, "학생", "88");
        ReflectionTestUtils.setField(bill, "id", 100L);
        when(academic.get(7L)).thenReturn(candidate);
        when(academic.post(7L, "bill", 100L)).thenReturn(candidate);
        when(catalog.findSemester(1L)).thenReturn(new AcademicSemesterResponse(1L, LocalDate.of(2027,3,1), LocalDate.of(2027,6,30)));
        when(recorder.reserve(7L, "학생", 1L, request)).thenReturn(bill);
    }

    @Test
    void lostIssueResponseRecoversSamePgOrderWithoutIssuingAgain() {
        pending();
        var response = new TossVirtualAccountIssueResponse("pk", "secret", new TossVirtualAccountIssueResponse.VirtualAccountInfo("account", "88", "2027-01-01T23:59:59+09:00"));
        when(toss.findVirtualAccountByOrderId("ADMISSION-100")).thenReturn(response);
        when(toss.getPaymentByOrderId("ADMISSION-100")).thenReturn(new TossPaymentResponse("pk", "ADMISSION-100", "WAITING_FOR_DEPOSIT", 10000L));
        service.issue(7L, request, new CurrentUser(1L, "ADMIN"));
        verify(toss, never()).issueVirtualAccount(any(), any(), any(), any(), any(), any());
        verify(recorder).issued(100L, "ADMISSION-100", response, 1L);
    }

    @Test
    void cancelledPgOrderCannotBeRestoredAsPayableAccount() {
        pending();
        when(toss.findVirtualAccountByOrderId("ADMISSION-100"))
                .thenReturn(new TossVirtualAccountIssueResponse("pk", "secret", null));
        when(toss.getPaymentByOrderId("ADMISSION-100"))
                .thenReturn(new TossPaymentResponse("pk", "ADMISSION-100", "CANCELED", 10000L));
        assertThatThrownBy(() -> service.issue(7L, request, new CurrentUser(1L, "ADMIN")))
                .isInstanceOf(AdmissionPaymentConflictException.class);
        verify(recorder, never()).issued(any(), any(), any(), any());
    }

    @Test
    void cancelledCandidateNeverIssuesVirtualAccount() {
        when(academic.get(7L)).thenReturn(new AdmissionAcademicClient.Candidate(7L, "학생", (short)2027, "CANCELLED", null, false, null, 3L));
        assertThatThrownBy(() -> service.issue(7L, request, new CurrentUser(1L, "ADMIN")))
                .isInstanceOf(AdmissionPaymentConflictException.class);
        verifyNoInteractions(toss, recorder);
    }

    private TuitionBill expired() {
        when(academic.get(7L)).thenReturn(new AdmissionAcademicClient.Candidate(7L, "학생", (short)2027, "PENDING", 100L, false, null, 3L));
        var bill = new TuitionBill(null, 1L, request.billingAmount(), LocalDate.now().minusDays(1), TuitionBillStatus.UNPAID, 1L);
        bill.admission(7L, "학생", "88"); ReflectionTestUtils.setField(bill, "id", 100L);
        var old = new VirtualAccount(100L, "ADMISSION-100", "secret", "account", "88", LocalDateTime.now().minusDays(1), VirtualAccountStatus.EXPIRED);
        old.assignPaymentKey("old-pk"); ReflectionTestUtils.setField(old, "id", 10L);
        when(bills.findByAdmissionCandidateId(7L)).thenReturn(Optional.of(bill));
        when(accounts.findById(10L)).thenReturn(Optional.of(old));
        return bill;
    }

    @Test
    void paidPgOldOrderPreventsReissueEvenWhenLocalWebhookIsMissing() {
        expired();
        when(toss.getPaymentByOrderId("ADMISSION-100"))
                .thenReturn(new TossPaymentResponse("old-pk", "ADMISSION-100", "DONE", 10000L));
        assertThatThrownBy(() -> service.reissue(7L, new AdmissionReissueRequest(10L, request.dueDate()), new CurrentUser(1L, "ADMIN")))
                .isInstanceOf(AdmissionPaymentConflictException.class);
        verifyNoInteractions(recorder);
        verify(toss, never()).cancelPayment(any(), any(), any(), any(), any());
        verify(toss, never()).issueVirtualAccount(any(), any(), any(), any(), any(), any());
    }

    @Test
    void lostReissueResponseCanRecoverAfterReservedDueDateWithoutAnotherIssue() {
        var bill = expired();
        var pastDate = LocalDate.now().minusDays(1);
        bill.prepareAdmissionReissue("ADMISSION-100-R10", pastDate);
        var retry = new AdmissionReissueRequest(10L, pastDate);
        when(recorder.prepareReissue(100L, retry)).thenReturn(bill);
        var response = new TossVirtualAccountIssueResponse("new-pk", "new-secret",
                new TossVirtualAccountIssueResponse.VirtualAccountInfo("account", "88", pastDate+"T23:59:59+09:00"));
        when(toss.findVirtualAccountByOrderId("ADMISSION-100-R10")).thenReturn(response);
        when(toss.getPaymentByOrderId("ADMISSION-100-R10"))
                .thenReturn(new TossPaymentResponse("new-pk", "ADMISSION-100-R10", "EXPIRED", 10000L));
        service.reissue(7L, retry, new CurrentUser(1L, "ADMIN"));
        verify(recorder).issued(100L, "ADMISSION-100-R10", response, 1L);
        verify(toss, never()).getPaymentByOrderId("ADMISSION-100");
        verify(toss, never()).issueVirtualAccount(any(), any(), any(), any(), any(), any());
    }
}

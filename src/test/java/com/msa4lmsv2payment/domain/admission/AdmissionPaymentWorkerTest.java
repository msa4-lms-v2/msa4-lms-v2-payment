package com.msa4lmsv2payment.domain.admission;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.msa4lmsv2payment.domain.tuitionbill.entity.*;
import com.msa4lmsv2payment.domain.tuitionbill.repository.TuitionBillRepository;
import com.msa4lmsv2payment.domain.virtualaccount.entity.*;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.global.client.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
class AdmissionPaymentWorkerTest {
    TuitionBillRepository bills=mock(TuitionBillRepository.class);
    VirtualAccountRepository accounts=mock(VirtualAccountRepository.class);
    AdmissionAcademicClient academic=mock(AdmissionAcademicClient.class);
    AdmissionPaymentRecorder recorder=mock(AdmissionPaymentRecorder.class);
    TossPaymentsClient toss=mock(TossPaymentsClient.class);
    com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountDepositRecorderService deposits=mock(com.msa4lmsv2payment.domain.virtualaccount.service.VirtualAccountDepositRecorderService.class);
    AdmissionPaymentWorker worker=new AdmissionPaymentWorker(bills,accounts,academic,recorder,toss,deposits);
    TuitionBill bill;
    @BeforeEach void setup() {
        bill=new TuitionBill(null,1L,new BigDecimal("10000"),LocalDate.now().plusDays(7),TuitionBillStatus.UNPAID,1L);
        ReflectionTestUtils.setField(bill,"id",100L);bill.admission(7L,"학생","88");ReflectionTestUtils.setField(bill,"admissionNextSyncAt",LocalDateTime.now().minusMinutes(1));
        when(bills.findById(100L)).thenReturn(Optional.of(bill));
        when(academic.get(7L)).thenReturn(candidate("PENDING",null));
    }
    AdmissionAcademicClient.Candidate candidate(String status,Long student) {return new AdmissionAcademicClient.Candidate(7L,"학생",(short)2027,status,100L,false,student,3L);}
    void paidAccount() {
        bill.changeStatus(TuitionBillStatus.PAID);
        var a=new VirtualAccount(100L,"ADMISSION-100","secret","account","88",LocalDateTime.now().plusDays(7),VirtualAccountStatus.DEPOSITED);
        a.assignPaymentKey("pk");when(accounts.findByOrderId("ADMISSION-100")).thenReturn(Optional.of(a));
    }
    @Test void unpaidAndPartialNeverRequestAccountCreation() {
        worker.synchronizeBill(100L);bill.changeStatus(TuitionBillStatus.PARTIAL);worker.synchronizeBill(100L);
        verify(academic,never()).post(any(),any(),any());verifyNoInteractions(toss);
    }
    @Test void fullPaymentRequiresCurrentPgStateAndNoRefund() {
        paidAccount();when(toss.getPaymentByOrderId("ADMISSION-100")).thenReturn(new TossPaymentResponse("pk","ADMISSION-100","WAITING_FOR_DEPOSIT",10000L));
        worker.synchronizeBill(100L);
        when(toss.getPaymentByOrderId("ADMISSION-100")).thenReturn(new TossPaymentResponse("pk","ADMISSION-100","DONE",10000L,9000L));worker.synchronizeBill(100L);
        verify(academic,never()).post(any(),any(),any());verify(recorder,never()).synced(any(),any());
    }
    @Test void completedAdmissionLinksExistingBillToStudent() {
        paidAccount();when(toss.getPaymentByOrderId("ADMISSION-100")).thenReturn(new TossPaymentResponse("pk","ADMISSION-100","DONE",10000L));
        when(academic.post(7L,"paid",100L)).thenReturn(candidate("COMPLETED",20L));worker.synchronizeBill(100L);
        verify(recorder).synced(100L,20L);
        bill.linkAdmissionStudent(20L);bill.linkAdmissionStudent(20L);
        assertThatThrownBy(()->bill.linkAdmissionStudent(21L)).isInstanceOf(IllegalStateException.class);
    }
    @Test void lostWebhookIsRecoveredBeforeAccountCreation() {
        paidAccount();bill.changeStatus(TuitionBillStatus.UNPAID);
        when(toss.getPaymentByOrderId("ADMISSION-100"))
                .thenReturn(new TossPaymentResponse("pk","ADMISSION-100","DONE",10000L,10000L,"pg-deposit-key"));
        worker.synchronizeBill(100L);
        verify(deposits).recordDeposit(isNull(),eq(new BigDecimal("10000")),eq("pg-deposit-key"),eq("admission-reconcile-pg-deposit-key"),any());
        verify(academic,never()).post(any(),any(),any());
    }
    @Test void cancelledPaidAdmissionGoesToRefundNotProvisioning() {
        paidAccount();when(academic.get(7L)).thenReturn(candidate("CANCELLED",null));worker.synchronizeBill(100L);
        verify(recorder).cancelled(100L);verify(academic,never()).post(any(),any(),any());
    }
}

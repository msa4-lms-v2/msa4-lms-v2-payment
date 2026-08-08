package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBill;
import com.msa4lmsv2payment.domain.tuitionbill.entity.TuitionBillStatus;
import com.msa4lmsv2payment.domain.tuitionbill.service.TuitionBillService;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.global.error.VirtualAccountNotFoundException;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.VirtualAccountIssueRequestDTO;
import com.msa4lmsv2payment.global.audit.AuditLogRecorder;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.client.TossVirtualAccountIssueResponse;
import com.msa4lmsv2payment.global.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualAccountServiceTest {

    @Mock
    private VirtualAccountRepository virtualAccountRepository;
    @Mock
    private TuitionBillService tuitionBillService;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    @InjectMocks
    private VirtualAccountService virtualAccountService;

    private static TuitionBill tuitionBill(Long id, BigDecimal amount) {
        TuitionBill bill = new TuitionBill(20260001L, 1L, amount, LocalDate.of(2026, 9, 1), TuitionBillStatus.UNPAID, 1L);
        try {
            Field idField = TuitionBill.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(bill, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return bill;
    }

    // SCRUM-55: 가상계좌 발급
    @Test
    void 가상계좌_발급은_토스_응답의_계좌번호를_저장한다() {
        CurrentUser admin = new CurrentUser(1L, "ADMIN");
        TuitionBill bill = tuitionBill(1L, BigDecimal.valueOf(4_200_000));
        when(tuitionBillService.getOwnedTuitionBillOrThrow(admin, 1L)).thenReturn(bill);
        when(tossPaymentsClient.issueVirtualAccount(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new TossVirtualAccountIssueResponse(
                        new TossVirtualAccountIssueResponse.VirtualAccountInfo("X1234567890", "020", "2026-09-08T00:00:00")));
        when(virtualAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = virtualAccountService.issueVirtualAccount(admin, new VirtualAccountIssueRequestDTO(1L, "020", "홍길동"));

        assertThat(result.accountNumber()).isEqualTo("X1234567890");
        assertThat(result.tuitionBillId()).isEqualTo(1L);
    }

    // SCRUM-175 선행 조건 - 발급된 계좌가 없으면 조회 실패
    @Test
    void 발급된_가상계좌가_없으면_예외() {
        when(virtualAccountRepository.findByTuitionBillId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> virtualAccountService.getByTuitionBillIdOrThrow(1L))
                .isInstanceOf(VirtualAccountNotFoundException.class);
    }
}

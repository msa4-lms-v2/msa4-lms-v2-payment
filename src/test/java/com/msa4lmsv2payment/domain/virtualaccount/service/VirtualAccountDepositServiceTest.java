package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountDepositRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.TossVirtualAccountDepositWebhookRequest;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.error.VirtualAccountSecretMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualAccountDepositServiceTest {

    @Mock VirtualAccountRepository virtualAccountRepository;
    @Mock VirtualAccountDepositRepository depositRepository;
    @Mock TossPaymentsClient tossPaymentsClient;
    @Mock VirtualAccountDepositRecorderService depositRecorder;
    @Mock VirtualAccount virtualAccount;

    private VirtualAccountDepositService service;

    @BeforeEach
    void setUp() {
        service = new VirtualAccountDepositService(
                virtualAccountRepository, depositRepository, tossPaymentsClient, depositRecorder);
    }

    @Test
    void 정상_입금은_토스_재조회_후_저장한다() {
        TossVirtualAccountDepositWebhookRequest request = request();
        when(virtualAccountRepository.findByOrderId("ORDER-1")).thenReturn(Optional.of(virtualAccount));
        when(virtualAccount.matchesSecret("secret")).thenReturn(true);
        when(virtualAccount.getId()).thenReturn(1L);
        when(tossPaymentsClient.getPaymentByOrderId("ORDER-1"))
                .thenReturn(new TossPaymentResponse("payment-key", "ORDER-1", "DONE", 10000L));

        service.processDeposit("event-1", Instant.now().toString(), request);

        verify(depositRecorder).recordDeposit(eq(1L), eq(BigDecimal.valueOf(10000L)),
                eq("transaction-1"), eq("event-1"), any(LocalDateTime.class));
    }

    @Test
    void 같은_전송_ID는_중복_처리하지_않는다() {
        when(virtualAccountRepository.findByOrderId("ORDER-1")).thenReturn(Optional.of(virtualAccount));
        when(virtualAccount.matchesSecret("secret")).thenReturn(true);
        when(depositRepository.existsByWebhookEventId("event-1")).thenReturn(true);

        service.processDeposit("event-1", Instant.now().toString(), request());

        verify(tossPaymentsClient, never()).getPaymentByOrderId(any());
        verify(depositRecorder, never()).recordDeposit(any(), any(), any(), any(), any());
    }

    @Test
    void secret이_다르면_차단한다() {
        when(virtualAccountRepository.findByOrderId("ORDER-1")).thenReturn(Optional.of(virtualAccount));
        when(virtualAccount.matchesSecret("secret")).thenReturn(false);

        assertThrows(VirtualAccountSecretMismatchException.class,
                () -> service.processDeposit("event-1", Instant.now().toString(), request()));

        verify(tossPaymentsClient, never()).getPaymentByOrderId(any());
    }

    @Test
    void 오래된_전송은_차단한다() {
        assertThrows(VirtualAccountSecretMismatchException.class,
                () -> service.processDeposit("event-1", Instant.now().minusSeconds(601).toString(), request()));

        verify(virtualAccountRepository, never()).findByOrderId(any());
    }

    private TossVirtualAccountDepositWebhookRequest request() {
        return new TossVirtualAccountDepositWebhookRequest(
                "secret", "DONE", "transaction-1", "ORDER-1", LocalDateTime.now().toString());
    }
}

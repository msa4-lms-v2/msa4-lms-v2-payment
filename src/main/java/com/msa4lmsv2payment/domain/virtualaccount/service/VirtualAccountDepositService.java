package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountDepositRepository;
import com.msa4lmsv2payment.domain.virtualaccount.repository.VirtualAccountRepository;
import com.msa4lmsv2payment.domain.virtualaccount.request.TossVirtualAccountDepositWebhookRequest;
import com.msa4lmsv2payment.global.client.TossPaymentResponse;
import com.msa4lmsv2payment.global.client.TossPaymentsClient;
import com.msa4lmsv2payment.global.error.VirtualAccountNotFoundException;
import com.msa4lmsv2payment.global.error.VirtualAccountSecretMismatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 가상계좌 입금 Webhook 처리.
 * Webhook은 로그인 사용자가 없는 시스템 요청이라 감사 로그의 actor_id는 예약 값 0(SYSTEM)을 쓴다(VirtualAccountDepositRecorderService).
 * 토스 문서(webhook-events) 기준 DEPOSIT_CALLBACK 본문은 secret/status/transactionKey/orderId/createdAt 5개뿐이고
 * 금액이 없어, 실제 입금액은 TossPaymentsClient.getPaymentByOrderId로 다시 조회해 확인한다 - 본문을 그대로 신뢰하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAccountDepositService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountDepositRepository virtualAccountDepositRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final VirtualAccountDepositRecorderService depositRecorder;

    // 토스 재조회(외부 호출)를 트랜잭션 밖에서 실행하고, 실제 저장은 depositRecorder(별도 트랜잭션)에 위임한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processDeposit(TossVirtualAccountDepositWebhookRequest webhook) {
        VirtualAccount virtualAccount = virtualAccountRepository.findByOrderId(webhook.orderId())
                .orElseThrow(() -> new VirtualAccountNotFoundException("존재하지 않는 orderId입니다: " + webhook.orderId()));

        if (!virtualAccount.matchesSecret(webhook.secret())) {
            throw new VirtualAccountSecretMismatchException("가상계좌 입금 Webhook의 secret이 일치하지 않습니다.");
        }

        if (virtualAccountDepositRepository.existsByTossTransactionKey(webhook.transactionKey())) {
            log.info("이미 처리된 가상계좌 입금 Webhook, 무시함 [orderId={}, transactionKey={}]", webhook.orderId(), webhook.transactionKey());
            return;
        }

        TossPaymentResponse tossPayment = tossPaymentsClient.getPaymentByOrderId(webhook.orderId());
        if (!tossPayment.isDone() || tossPayment.totalAmount() == null) {
            log.info("입금 미완료 상태의 Webhook, 무시함 [orderId={}, status={}]", webhook.orderId(), tossPayment.status());
            return;
        }

        depositRecorder.recordDeposit(virtualAccount.getId(), BigDecimal.valueOf(tossPayment.totalAmount()), webhook.transactionKey());
    }
}

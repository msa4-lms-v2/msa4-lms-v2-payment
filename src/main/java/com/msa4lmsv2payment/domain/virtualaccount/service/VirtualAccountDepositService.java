package com.msa4lmsv2payment.domain.virtualaccount.service;

import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccount;
import com.msa4lmsv2payment.domain.virtualaccount.entity.VirtualAccountStatus;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

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

    private static final Duration MAX_TRANSMISSION_AGE = Duration.ofMinutes(10);
    private static final ZoneId TOSS_ZONE = ZoneId.of("Asia/Seoul");

    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountDepositRepository virtualAccountDepositRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final VirtualAccountDepositRecorderService depositRecorder;

    // 토스 재조회(외부 호출)를 트랜잭션 밖에서 실행하고, 실제 저장은 depositRecorder(별도 트랜잭션)에 위임한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processDeposit(String transmissionId, String transmissionTime,
                               TossVirtualAccountDepositWebhookRequest webhook) {
        if (transmissionId == null || transmissionId.isBlank()) {
            throw new VirtualAccountSecretMismatchException("Webhook 전송 ID가 비어 있습니다.");
        }
        Instant transmittedAt = parseTimestamp(transmissionTime);
        Instant eventCreatedAt = parseTimestamp(webhook.createdAt());
        Instant now = Instant.now();
        if (transmittedAt.isBefore(now.minus(MAX_TRANSMISSION_AGE)) || transmittedAt.isAfter(now.plusSeconds(60))) {
            throw new VirtualAccountSecretMismatchException("Webhook 전송 시간이 허용 범위를 벗어났습니다.");
        }

        VirtualAccount virtualAccount = virtualAccountRepository.findByOrderId(webhook.orderId())
                .orElseThrow(() -> new VirtualAccountNotFoundException("존재하지 않는 orderId입니다: " + webhook.orderId()));

        if (!virtualAccount.matchesSecret(webhook.secret())) {
            throw new VirtualAccountSecretMismatchException("가상계좌 입금 Webhook의 secret이 일치하지 않습니다.");
        }

        if (virtualAccountDepositRepository.existsByWebhookEventId(transmissionId)
                || virtualAccountDepositRepository.existsByTossTransactionKey(webhook.transactionKey())) {
            log.info("이미 처리된 가상계좌 입금 Webhook, 무시함 [orderId={}, transactionKey={}]", webhook.orderId(), webhook.transactionKey());
            return;
        }

        TossPaymentResponse tossPayment = tossPaymentsClient.getPaymentByOrderId(webhook.orderId());
        if (!webhook.orderId().equals(tossPayment.orderId()) || !webhook.status().equals(tossPayment.status())) {
            throw new VirtualAccountSecretMismatchException("Webhook 내용과 Toss 결제 조회 결과가 일치하지 않습니다.");
        }
        if (!tossPayment.isDone() || tossPayment.totalAmount() == null) {
            log.info("입금 미완료 상태의 Webhook, 무시함 [orderId={}, status={}]", webhook.orderId(), tossPayment.status());
            return;
        }

        try {
            depositRecorder.recordDeposit(virtualAccount.getId(), BigDecimal.valueOf(tossPayment.totalAmount()),
                    webhook.transactionKey(), transmissionId, eventCreatedAt.atZone(TOSS_ZONE).toLocalDateTime());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("동시에 중복 수신된 가상계좌 Webhook, 무시함 [eventId={}, transactionKey={}]",
                    transmissionId, webhook.transactionKey());
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                long epoch = Long.parseLong(value);
                return value.length() > 10 ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
            }
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeException ignored) {
                return LocalDateTime.parse(value).atZone(TOSS_ZONE).toInstant();
            }
        } catch (DateTimeException | NumberFormatException e) {
            throw new VirtualAccountSecretMismatchException("Webhook timestamp 형식이 올바르지 않습니다.");
        }
        BigDecimal amount = BigDecimal.valueOf(tossPayment.totalAmount());
        if (virtualAccount.getStatus() == VirtualAccountStatus.EXPIRED) {
            // 만료된 계좌로 실제 돈이 들어온 것이므로 입금 기록은 남기되, 정상 완납 흐름(고지 상태 변경) 대신
            // 전액 환불 요청을 자동 생성한다 - 사용자 확인(2026-09-07) 반영.
            log.warn("만료된 가상계좌로 입금됨, 전액 환불 요청 생성 [orderId={}, virtualAccountId={}]", webhook.orderId(), virtualAccount.getId());
            depositRecorder.recordExpiredAccountDeposit(virtualAccount.getId(), amount, webhook.transactionKey());
            return;
        }

        depositRecorder.recordDeposit(virtualAccount.getId(), amount, webhook.transactionKey());
    }
}

package com.msa4lmsv2payment.domain.virtualaccount.request;

/**
 * 토스페이먼츠 가상계좌 입금 Webhook(DEPOSIT_CALLBACK) 본문.
 * 공식 문서(docs.tosspayments.com/reference/using-api/webhook-events) 기준 필드는
 * createdAt, secret, status, transactionKey, orderId 5개뿐이고 금액은 포함되지 않는다.
 * 실제 입금액은 TossPaymentsClient.getPaymentByOrderId로 다시 조회해 확인한다.
 * secret은 발급 응답의 virtualAccount.secret과 대조해 위조 요청을 막는 용도다.
 * transactionKey는 같은 거래의 중복 통보를 걸러내는 고유 식별자다.
 */
public record TossVirtualAccountDepositWebhookRequest(
        String secret,
        String status,
        String transactionKey,
        String orderId,
        String createdAt
) {
}

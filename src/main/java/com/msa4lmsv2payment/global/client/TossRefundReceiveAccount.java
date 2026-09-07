package com.msa4lmsv2payment.global.client;

/**
 * 토스페이먼츠 결제 취소(POST /v1/payments/{paymentKey}/cancel) 요청의 refundReceiveAccount.
 * 가상계좌(WITHDRAWAL/EXCESS_DEPOSIT) 환불에서만 필수이고, 카드(PG_CANCEL) 취소에는 쓰지 않는다.
 */
public record TossRefundReceiveAccount(String bankCode, String accountNumber, String holderName) {
}

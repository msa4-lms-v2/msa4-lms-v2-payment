package com.msa4lmsv2payment.global.client;

/**
 * 토스페이먼츠 결제 승인(POST /v1/payments/confirm)·조회(GET /v1/payments/{paymentKey}) 공용 응답.
 * status: READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED.
 * 실제 토스 테스트 상점 키로 검증 전이라 필드명이 응답과 정확히 일치하는지는 미확인 상태다.
 */
public record TossPaymentResponse(String paymentKey, String orderId, String status, Long totalAmount) {

    public boolean isDone() {
        return "DONE".equals(status);
    }
}

package com.msa4lmsv2payment.global.client;

/**
 * 토스페이먼츠 POST /v1/virtual-accounts 응답 중 실제 사용하는 필드만 추린 것.
 * 원 응답은 Payment 객체 안에 virtualAccount가 중첩된 구조라, Jackson이 이 필드들만 골라 채운다
 * (나머지 필드는 무시됨 - 알 수 없는 프로퍼티 무시가 기본 동작).
 * 실제 토스 테스트 상점 키로 검증 전이라 필드명이 응답과 정확히 일치하는지는 미확인 상태다(TOSS_SECRET_KEY 발급 후 확인 필요).
 */
public record TossVirtualAccountIssueResponse(
        String accountNumber,
        String bankCode,
        String dueDate
) {
}

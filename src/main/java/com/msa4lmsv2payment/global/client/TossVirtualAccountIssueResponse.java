package com.msa4lmsv2payment.global.client;

/**
 * 토스페이먼츠 POST /v1/virtual-accounts 응답 중 실제 사용하는 필드만 추린 것.
 * 응답 최상위는 Payment 객체이고, 계좌 정보는 그 안의 virtualAccount 객체에 중첩돼 있다
 * (실제 테스트 상점 키로 검증해 확인함 - 나머지 필드는 무시됨, 알 수 없는 프로퍼티 무시가 기본 동작).
 */
public record TossVirtualAccountIssueResponse(VirtualAccountInfo virtualAccount) {

    public record VirtualAccountInfo(
            String accountNumber,
            String bankCode,
            String dueDate
    ) {
    }
}

package com.msa4lmsv2payment.global.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayContextVerifierTest {

    private final GatewayContextVerifier verifier = new GatewayContextVerifier();

    @Test
    void 유효한_사용자ID와_허용된_역할이면_유효하다() {
        assertThat(verifier.isValid("1", "STUDENT")).isTrue();
    }

    @Test
    void 사용자ID가_양수가_아니면_거부된다() {
        assertThat(verifier.isValid("0", "STUDENT")).isFalse();
        assertThat(verifier.isValid("-1", "STUDENT")).isFalse();
    }

    @Test
    void 사용자ID가_숫자가_아니면_거부된다() {
        assertThat(verifier.isValid("abc", "STUDENT")).isFalse();
    }

    @Test
    void 허용되지_않은_역할은_거부된다() {
        assertThat(verifier.isValid("1", "ROOT")).isFalse();
    }

    @Test
    void 사용자ID나_역할이_비어있으면_거부된다() {
        assertThat(verifier.isValid(null, "STUDENT")).isFalse();
        assertThat(verifier.isValid("1", null)).isFalse();
    }
}

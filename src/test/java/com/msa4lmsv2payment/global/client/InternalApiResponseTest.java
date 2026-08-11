package com.msa4lmsv2payment.global.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiResponseTest {

    @Test
    void 공통_성공코드_00만_성공으로_판정한다() {
        assertThat(new InternalApiResponse<>("00", "정상 처리되었습니다.", "data").isSuccess()).isTrue();
        assertThat(new InternalApiResponse<>("E10", "데이터가 없습니다.", null).isSuccess()).isFalse();
    }
}

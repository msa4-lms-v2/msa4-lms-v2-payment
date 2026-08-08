package com.msa4lmsv2payment.global.client;

import com.msa4lmsv2payment.global.error.NotStudentAccountException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicStubClientTest {

    private final AcademicStubClient client = new AcademicStubClient();

    @Test
    void 등록된_userId는_고정_매핑된_학번을_반환한다() {
        AcademicStudentResponse result = client.findStudentByUserId(1L);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.id()).isEqualTo(20260001L);
        assertThat(result.id()).isNotEqualTo(result.userId());
    }

    @Test
    void 등록되지_않은_userId는_NotStudentAccountException() {
        assertThatThrownBy(() -> client.findStudentByUserId(999L))
                .isInstanceOf(NotStudentAccountException.class);
    }
}

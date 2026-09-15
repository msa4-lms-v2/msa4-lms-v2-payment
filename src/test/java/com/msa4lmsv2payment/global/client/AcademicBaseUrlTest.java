package com.msa4lmsv2payment.global.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AcademicBaseUrlTest {
    @Test void 운영의_기존_주소를_사용하고_명시적_Academic_주소를_우선한다() throws Exception {
        var env = new MockEnvironment().withProperty("GATEWAY_INTERNAL_BASE_URL", "http://academic-service:8080");
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            env.getPropertySources().addLast(source);
        }
        assertEquals("http://academic-service:8080", env.getProperty("academic.internal.base-url"));
        env.setProperty("ACADEMIC_INTERNAL_BASE_URL", "http://academic-explicit:8080");
        assertEquals("http://academic-explicit:8080", env.getProperty("academic.internal.base-url"));
    }
}

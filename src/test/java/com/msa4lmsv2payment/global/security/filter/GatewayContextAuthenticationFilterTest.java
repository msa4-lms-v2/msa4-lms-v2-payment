package com.msa4lmsv2payment.global.security.filter;

import com.msa4lmsv2payment.global.security.CurrentUser;
import com.msa4lmsv2payment.global.security.GatewayContextVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayContextAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 검증된_Gateway_컨텍스트로_인증정보를_생성한다() throws Exception {
        GatewayContextVerifier verifier = mock(GatewayContextVerifier.class);
        when(verifier.isValid(anyString(), anyString())).thenReturn(true);
        GatewayContextAuthenticationFilter filter = new GatewayContextAuthenticationFilter(verifier);
        MockHttpServletRequest request = requestWithGatewayHeaders();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new CurrentUser(1L, "STUDENT"));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void 검증되지_않은_Gateway_컨텍스트는_인증하지_않는다() throws Exception {
        GatewayContextVerifier verifier = mock(GatewayContextVerifier.class);
        when(verifier.isValid(anyString(), anyString())).thenReturn(false);
        GatewayContextAuthenticationFilter filter = new GatewayContextAuthenticationFilter(verifier);

        filter.doFilter(requestWithGatewayHeaders(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithGatewayHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payment/student-tuition");
        request.addHeader(GatewayContextAuthenticationFilter.USER_ID_HEADER, "1");
        request.addHeader(GatewayContextAuthenticationFilter.USER_ROLE_HEADER, "STUDENT");
        return request;
    }
}

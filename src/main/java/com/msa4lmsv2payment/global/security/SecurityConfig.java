package com.msa4lmsv2payment.global.security;

import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs",
            "/api-docs/**"
    };

    private final GatewayContextAuthenticationFilter gatewayContextAuthenticationFilter;
    private final GatewayAuthenticationEntryPoint gatewayAuthenticationEntryPoint;
    private final GatewayAccessDeniedHandler gatewayAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers(SWAGGER_PATHS).permitAll();
                    auth.requestMatchers("/api/payment/webhooks/**").permitAll();
                    // Academic이 호출하는 서비스 간 시스템 요청 - X-User-Id/X-User-Role을 싣지 않으므로 인증 대상에서 제외한다.
                    // 실제 보호는 SCG Internal listener + NetworkPolicy가 담당한다(docs-v2/MSA-LMS_INTEGRATION.md "서비스 시스템 요청").
                    auth.requestMatchers("/api/payment/academic-provision/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(gatewayAuthenticationEntryPoint)
                        .accessDeniedHandler(gatewayAccessDeniedHandler))
                .addFilterBefore(gatewayContextAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

package com.msa4lmsv2payment.global.security;

import com.msa4lmsv2payment.global.security.filter.GatewayContextAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayContextAuthenticationFilter gatewayContextAuthenticationFilter;
    private final GatewayAuthenticationEntryPoint gatewayAuthenticationEntryPoint;
    private final GatewayAccessDeniedHandler gatewayAccessDeniedHandler;
    private final Environment environment;

    @Bean
    public static Clock gatewayContextClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("stub"))) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(gatewayAuthenticationEntryPoint)
                        .accessDeniedHandler(gatewayAccessDeniedHandler))
                .addFilterBefore(gatewayContextAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

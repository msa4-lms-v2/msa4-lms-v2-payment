package com.msa4lmsv2payment.global.security.filter;

import com.msa4lmsv2payment.global.security.CurrentUser;
import com.msa4lmsv2payment.global.security.GatewayContextVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GatewayContextAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TIMESTAMP_HEADER = "X-Gateway-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Gateway-Signature";

    private final GatewayContextVerifier gatewayContextVerifier;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String signature = request.getHeader(SIGNATURE_HEADER);

        if (gatewayContextVerifier.isValid(
                userId,
                role,
                timestamp,
                request.getMethod(),
                request.getRequestURI(),
                signature)) {
            CurrentUser currentUser = new CurrentUser(Long.parseLong(userId), role);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}

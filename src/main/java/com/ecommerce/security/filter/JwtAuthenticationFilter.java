// File: src/main/java/com/ecommerce/security/filter/JwtAuthenticationFilter.java
package com.ecommerce.security.filter;

import com.ecommerce.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT authentication filter - runs once per request.
 *
 * Flow:
 * 1. Extract Bearer token from Authorization header
 * 2. Validate token signature + expiry
 * 3. Extract userId, email, role from claims (no DB hit needed)
 * 4. Set authentication in SecurityContext
 *
 * We don't look up the user from DB on every request (performance).
 * Instead we trust the signed JWT claims.
 * The only DB check happens at refresh token rotation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // If no Bearer token, skip - Spring Security will handle unauthenticated access
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract claims from the validated JWT (no DB call)
        UUID userId = jwtService.extractUserId(token);
        String role = jwtService.extractRole(token);
        String email = jwtService.extractEmail(token);

        // Build Spring Security authentication with ROLE_ prefix (Spring Security convention)
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var authentication = new UsernamePasswordAuthenticationToken(userId, email, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Store in SecurityContext - available throughout the request
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: userId={}, role={}", userId, role);

        filterChain.doFilter(request, response);
    }
}

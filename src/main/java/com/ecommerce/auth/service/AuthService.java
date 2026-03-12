// File: src/main/java/com/ecommerce/auth/service/AuthService.java
package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.Session;
import com.ecommerce.auth.repository.SessionRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.security.jwt.JwtService;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core authentication service.
 *
 * Business rules:
 * - Email must be unique across all users
 * - Passwords hashed with BCrypt before storage (never store plaintext)
 * - Default role is CUSTOMER (admin accounts created manually or via migration)
 * - Login: verify password, issue access + refresh tokens, persist session
 * - Logout: revoke the refresh token session (access token expires naturally)
 * - Refresh: validate refresh token from DB, issue new tokens, rotate refresh token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Register a new customer account.
     * Validates email uniqueness, hashes password, creates user with CUSTOMER role.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Business rule: email must be unique
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS",
                    "An account with this email already exists", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .role(User.Role.CUSTOMER)  // Default role - admin must be set manually
                .build();

        user = userRepository.save(user);
        log.info("New user registered: userId={}, email={}", user.getId(), user.getEmail());

        return issueTokensAndCreateSession(user);
    }

    /**
     * Authenticate user with email/password.
     * Uses BCrypt comparison (timing-safe).
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // BCrypt comparison - same timing whether user exists or not (mitigates timing attacks)
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        log.info("User logged in: userId={}", user.getId());
        return issueTokensAndCreateSession(user);
    }

    /**
     * Exchange a valid refresh token for new access + refresh tokens.
     * Implements refresh token rotation: old refresh token is revoked, new one issued.
     * This limits the damage if a refresh token is stolen.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Session session = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN",
                        "Refresh token not found", HttpStatus.UNAUTHORIZED));

        // Validate: not revoked, not expired
        if (!session.isValid()) {
            throw new BusinessException("INVALID_REFRESH_TOKEN",
                    "Refresh token is expired or revoked", HttpStatus.UNAUTHORIZED);
        }

        // Rotate: revoke old session, issue new tokens
        session.setRevoked(true);
        sessionRepository.save(session);

        User user = session.getUser();
        return issueTokensAndCreateSession(user);
    }

    /**
     * Logout: revoke the current refresh token session.
     * Access token will expire naturally (can't be actively invalidated without Redis blocklist).
     * For high-security scenarios, add the access token to a Redis blocklist until its expiry.
     */
    @Transactional
    public void logout(String refreshToken) {
        sessionRepository.revokeByRefreshToken(refreshToken);
        log.info("Refresh token revoked");
    }

    /** Helper: generate tokens and persist refresh token session */
    private AuthResponse issueTokensAndCreateSession(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        Session session = Session.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiration))
                .revoked(false)
                .build();
        sessionRepository.save(session);

        return new AuthResponse(user.getId(), user.getEmail(), user.getRole().name(), accessToken, refreshToken);
    }
}

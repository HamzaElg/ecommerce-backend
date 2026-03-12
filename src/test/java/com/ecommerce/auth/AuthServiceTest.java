// File: src/test/java/com/ecommerce/auth/AuthServiceTest.java
package com.ecommerce.auth;

import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.Session;
import com.ecommerce.auth.repository.SessionRepository;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.security.jwt.JwtService;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock SessionRepository sessionRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604800000L);
    }

    @Test
    void register_newUser_success() {
        var request = new RegisterRequest("test@example.com", "password123", "John", "Doe");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        when(sessionRepository.save(any())).thenReturn(new Session());

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_duplicateEmail_throwsBusinessException() {
        var request = new RegisterRequest("existing@example.com", "password123", "A", "B");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void login_validCredentials_returnsTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@e.com").passwordHash("hash")
                .role(User.Role.CUSTOMER).firstName("A").lastName("B").build();

        when(userRepository.findByEmail("u@e.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        when(sessionRepository.save(any())).thenReturn(new Session());

        var response = authService.login(new LoginRequest("u@e.com", "pw"));

        assertThat(response.email()).isEqualTo("u@e.com");
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        User user = User.builder().email("u@e.com").passwordHash("hash")
                .role(User.Role.CUSTOMER).build();
        when(userRepository.findByEmail("u@e.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("u@e.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logout_revokesSession() {
        authService.logout("someRefreshToken");
        verify(sessionRepository).revokeByRefreshToken("someRefreshToken");
    }

    @Test
    void refresh_validToken_returnsNewTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@e.com")
                .role(User.Role.CUSTOMER).firstName("A").lastName("B").build();
        Session session = Session.builder()
                .user(user).refreshToken("old-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false).build();

        when(sessionRepository.findByRefreshToken("old-token")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any())).thenReturn("new-refresh");

        var response = authService.refresh("old-token");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(session.isRevoked()).isTrue();  // Old session revoked
    }
}

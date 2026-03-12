// File: src/main/java/com/ecommerce/auth/repository/SessionRepository.java
package com.ecommerce.auth.repository;

import com.ecommerce.auth.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshToken(String refreshToken);

    @Modifying
    @Query("UPDATE Session s SET s.revoked = true WHERE s.refreshToken = :token")
    void revokeByRefreshToken(String token);

    /** Cleanup job: delete expired or revoked sessions to keep table small */
    @Modifying
    @Query("DELETE FROM Session s WHERE s.revoked = true OR s.expiresAt < :now")
    int deleteExpiredSessions(Instant now);
}

package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class TokenRepository {

    private final DataSource dataSource;

    public TokenRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertEmailVerification(UUID userId, String tokenHash, Instant expiresAt) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO email_verification_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)
                        """)) {
            ps.setObject(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public Optional<UUID> consumeEmailVerification(String tokenHash) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE email_verification_tokens SET used_at = now()
                        WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id
                        """)) {
            ps.setString(1, tokenHash);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    public void insertPasswordReset(UUID userId, String tokenHash, Instant expiresAt) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)
                        """)) {
            ps.setObject(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public Optional<UUID> consumePasswordReset(String tokenHash) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE password_reset_tokens SET used_at = now()
                        WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id
                        """)) {
            ps.setString(1, tokenHash);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }
}

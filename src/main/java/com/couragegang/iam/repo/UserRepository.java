package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UUID> findActiveIdByEmailLower(String emailLower) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id FROM users
                        WHERE lower(email) = ? AND deleted_at IS NULL AND status = 'active'
                        """)) {
            ps.setString(1, emailLower);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<UserRow> findById(UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, email, email_verified_at, status, display_name, locale
                        FROM users WHERE id = ? AND deleted_at IS NULL
                        """)) {
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public UUID insertUser(String email, String displayName, String locale) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO users (email, display_name, locale, status)
                        VALUES (?, ?, ?, 'active')
                        RETURNING id
                        """)) {
            ps.setString(1, email);
            ps.setString(2, displayName);
            ps.setString(3, locale == null ? "ru" : locale);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public void setEmailVerified(UUID userId, Instant at) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE users SET email_verified_at = ?, updated_at = now() WHERE id = ?
                        """)) {
            ps.setTimestamp(1, Timestamp.from(at));
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateProfile(UUID userId, String displayName, String locale) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE users SET display_name = COALESCE(?, display_name),
                        locale = COALESCE(?, locale), updated_at = now() WHERE id = ?
                        """)) {
            ps.setString(1, displayName);
            ps.setString(2, locale);
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
    }

    public void updatePasswordHash(UUID userId, String hash) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE user_passwords SET password_hash = ?, rotated_at = now(), must_change = false
                        WHERE user_id = ?
                        """)) {
            ps.setString(1, hash);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
    }

    public void insertPassword(UUID userId, String hash) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO user_passwords (user_id, password_hash) VALUES (?, ?)
                        """)) {
            ps.setObject(1, userId);
            ps.setString(2, hash);
            ps.executeUpdate();
        }
    }

    public Optional<String> findPasswordHash(UUID userId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("SELECT password_hash FROM user_passwords WHERE user_id = ?")) {
            ps.setObject(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString(1));
                }
            }
        }
        return Optional.empty();
    }

    private static UserRow map(ResultSet rs) throws SQLException {
        Timestamp ev = rs.getTimestamp("email_verified_at");
        return new UserRow(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                ev == null ? null : ev.toInstant(),
                rs.getString("status"),
                rs.getString("display_name"),
                rs.getString("locale"));
    }

    public record UserRow(
            UUID id, String email, Instant emailVerifiedAt, String status, String displayName, String locale) {}
}

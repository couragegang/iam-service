package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class RefreshSessionRepository {

    private final DataSource dataSource;

    public RefreshSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record SessionRow(
            UUID id,
            UUID userId,
            UUID orgId,
            UUID familyId,
            Instant createdAt,
            Instant expiresAt,
            String refreshTokenHash) {}

    public SessionRow insert(
            UUID userId, UUID orgId, UUID familyId, String jti, Instant expiresAt, String refreshTokenHash)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO refresh_sessions (user_id, org_id, jti, family_id, expires_at, refresh_token_hash)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id, created_at
                        """)) {
            ps.setObject(1, userId);
            if (orgId == null) {
                ps.setNull(2, java.sql.Types.OTHER);
            } else {
                ps.setObject(2, orgId);
            }
            ps.setString(3, jti);
            ps.setObject(4, familyId);
            ps.setTimestamp(5, Timestamp.from(expiresAt));
            ps.setString(6, refreshTokenHash);
            try (var rs = ps.executeQuery()) {
                rs.next();
                var id = rs.getObject(1, UUID.class);
                var created = rs.getTimestamp(2).toInstant();
                return new SessionRow(id, userId, orgId, familyId, created, expiresAt, refreshTokenHash);
            }
        }
    }

    public Optional<SessionRow> findActiveByRefreshHash(String refreshHash) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, user_id, org_id, family_id, created_at, expires_at, refresh_token_hash
                        FROM refresh_sessions
                        WHERE refresh_token_hash = ? AND revoked_at IS NULL AND expires_at > now()
                        """)) {
            ps.setString(1, refreshHash);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new SessionRow(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getObject(3, UUID.class),
                                rs.getObject(4, UUID.class),
                                rs.getTimestamp(5).toInstant(),
                                rs.getTimestamp(6).toInstant(),
                                rs.getString(7)));
            }
        }
    }

    public void revoke(UUID sessionId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE refresh_sessions SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL
                        """)) {
            ps.setObject(1, sessionId);
            ps.executeUpdate();
        }
    }

    public void revokeFamily(UUID familyId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE refresh_sessions SET revoked_at = now()
                        WHERE family_id = ? AND revoked_at IS NULL
                        """)) {
            ps.setObject(1, familyId);
            ps.executeUpdate();
        }
    }

    public List<SessionListRow> listActiveByUser(UUID userId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, created_at, expires_at, org_id, device_label
                        FROM refresh_sessions
                        WHERE user_id = ? AND revoked_at IS NULL AND expires_at > now()
                        ORDER BY created_at DESC
                        """)) {
            ps.setObject(1, userId);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<SessionListRow>();
                while (rs.next()) {
                    out.add(new SessionListRow(
                            rs.getObject(1, UUID.class),
                            rs.getTimestamp(2).toInstant(),
                            rs.getTimestamp(3).toInstant(),
                            rs.getObject(4, UUID.class),
                            rs.getString(5)));
                }
                return out;
            }
        }
    }

    public record SessionListRow(UUID id, Instant createdAt, Instant expiresAt, UUID orgId, String deviceLabel) {}

    public boolean revokeIfOwned(UUID sessionId, UUID userId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE refresh_sessions SET revoked_at = now()
                        WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                        """)) {
            ps.setObject(1, sessionId);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
}

package com.couragegang.iam.repo;

import jakarta.annotation.Nullable;
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
public final class InviteRepository {

    private final DataSource dataSource;

    public InviteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record InviteRow(
            UUID id,
            String email,
            @Nullable UUID groupId,
            Instant expiresAt,
            Instant createdAt,
            Instant acceptedAt,
            List<String> roleKeys,
            List<String> groupRoleKeys) {}

    public UUID insertInvite(
            UUID orgId,
            @Nullable UUID groupId,
            String email,
            String tokenHash,
            Instant expiresAt,
            UUID createdByMembershipId)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO organization_invites (org_id, group_id, email, token_hash, expires_at, created_by_membership_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """)) {
            ps.setObject(1, orgId);
            if (groupId == null) {
                ps.setNull(2, java.sql.Types.OTHER);
            } else {
                ps.setObject(2, groupId);
            }
            ps.setString(3, email);
            ps.setString(4, tokenHash);
            ps.setTimestamp(5, Timestamp.from(expiresAt));
            if (createdByMembershipId == null) {
                ps.setNull(6, java.sql.Types.OTHER);
            } else {
                ps.setObject(6, createdByMembershipId);
            }
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public void attachGroupRoles(UUID inviteId, List<UUID> roleIds) throws SQLException {
        attachRolesToTable("organization_invite_group_roles", "invite_id", inviteId, roleIds);
    }

    public void attachRoles(UUID inviteId, List<UUID> roleIds) throws SQLException {
        attachRolesToTable("organization_invite_roles", "invite_id", inviteId, roleIds);
    }

    private void attachRolesToTable(String table, String inviteColumn, UUID inviteId, List<UUID> roleIds)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        "INSERT INTO " + table + " (" + inviteColumn + ", role_id) VALUES (?, ?)")) {
            for (UUID rid : roleIds) {
                ps.setObject(1, inviteId);
                ps.setObject(2, rid);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<InviteRow> listPending(UUID orgId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, email, group_id, expires_at, created_at, accepted_at
                        FROM organization_invites
                        WHERE org_id = ? AND revoked_at IS NULL AND accepted_at IS NULL
                        ORDER BY created_at DESC
                        """)) {
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<InviteRow>();
                while (rs.next()) {
                    var id = rs.getObject(1, UUID.class);
                    var email = rs.getString(2);
                    var gid = rs.getObject(3, UUID.class);
                    var exp = rs.getTimestamp(4).toInstant();
                    var cr = rs.getTimestamp(5).toInstant();
                    var acc = rs.getTimestamp(6);
                    var roles = loadInviteRoles(id);
                    var groupRoles = loadInviteGroupRoles(id);
                    out.add(new InviteRow(
                            id,
                            email,
                            gid,
                            exp,
                            cr,
                            acc == null ? null : acc.toInstant(),
                            roles,
                            groupRoles));
                }
                return out;
            }
        }
    }

    private List<String> loadInviteRoles(UUID inviteId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT r.key FROM organization_invite_roles ir
                        JOIN roles r ON r.id = ir.role_id
                        WHERE ir.invite_id = ?
                        ORDER BY r.key
                        """)) {
            ps.setObject(1, inviteId);
            try (var rs = ps.executeQuery()) {
                var keys = new ArrayList<String>();
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
                return keys;
            }
        }
    }

    public Optional<InviteAcceptData> findPendingByOrgAndTokenHash(UUID orgId, String tokenHash)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, email, group_id FROM organization_invites
                        WHERE org_id = ? AND token_hash = ? AND revoked_at IS NULL
                          AND accepted_at IS NULL AND expires_at > now()
                        """)) {
            ps.setObject(1, orgId);
            ps.setString(2, tokenHash);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var id = rs.getObject(1, UUID.class);
                var email = rs.getString(2);
                var groupId = rs.getObject(3, UUID.class);
                var roles = loadInviteRoles(id);
                var groupRoles = loadInviteGroupRoles(id);
                return Optional.of(new InviteAcceptData(id, email, groupId, roles, groupRoles));
            }
        }
    }

    private List<String> loadInviteGroupRoles(UUID inviteId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT r.key FROM organization_invite_group_roles ir
                        JOIN roles r ON r.id = ir.role_id
                        WHERE ir.invite_id = ?
                        ORDER BY r.key
                        """)) {
            ps.setObject(1, inviteId);
            try (var rs = ps.executeQuery()) {
                var keys = new ArrayList<String>();
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
                return keys;
            }
        }
    }

    public void markAccepted(UUID inviteId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE organization_invites SET accepted_at = now() WHERE id = ?
                        """)) {
            ps.setObject(1, inviteId);
            ps.executeUpdate();
        }
    }

    /**
     * Отзывает приглашение только если оно принадлежит организации {@code orgId}.
     *
     * @return {@code true}, если была обновлена ровно одна строка
     */
    public boolean revokeForOrg(UUID orgId, UUID inviteId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE organization_invites SET revoked_at = now()
                        WHERE id = ? AND org_id = ?
                        """)) {
            ps.setObject(1, inviteId);
            ps.setObject(2, orgId);
            return ps.executeUpdate() == 1;
        }
    }

    public record InviteAcceptData(
            UUID inviteId,
            String email,
            @Nullable UUID groupId,
            List<String> roleKeys,
            List<String> groupRoleKeys) {}
}

package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class MembershipRepository {

    private final DataSource dataSource;

    public MembershipRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID insert(UUID userId, UUID orgId, String status) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO organization_memberships (user_id, org_id, status, joined_at)
                        VALUES (?, ?, ?, CASE WHEN ? = 'active' THEN now() ELSE NULL END)
                        RETURNING id
                        """)) {
            ps.setObject(1, userId);
            ps.setObject(2, orgId);
            ps.setString(3, status);
            ps.setString(4, status);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public Optional<MembershipRow> findMembership(UUID orgId, UUID membershipId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT m.id, m.user_id, m.org_id, m.status, m.joined_at
                        FROM organization_memberships m
                        WHERE m.id = ? AND m.org_id = ?
                        """)) {
            ps.setObject(1, membershipId);
            ps.setObject(2, orgId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var mid = rs.getObject(1, UUID.class);
                var uid = rs.getObject(2, UUID.class);
                var oid = rs.getObject(3, UUID.class);
                var st = rs.getString(4);
                var jt = rs.getTimestamp(5);
                var roles = loadRoleKeys(mid);
                return Optional.of(
                        new MembershipRow(mid, uid, oid, st, roles, jt == null ? null : jt.toInstant()));
            }
        }
    }

    public Optional<MembershipRow> findByUserAndOrg(UUID userId, UUID orgId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT m.id, m.user_id, m.org_id, m.status, m.joined_at
                        FROM organization_memberships m
                        WHERE m.user_id = ? AND m.org_id = ?
                        """)) {
            ps.setObject(1, userId);
            ps.setObject(2, orgId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var mid = rs.getObject(1, UUID.class);
                var uid = rs.getObject(2, UUID.class);
                var oid = rs.getObject(3, UUID.class);
                var st = rs.getString(4);
                var jt = rs.getTimestamp(5);
                var roles = loadRoleKeys(mid);
                return Optional.of(
                        new MembershipRow(mid, uid, oid, st, roles, jt == null ? null : jt.toInstant()));
            }
        }
    }

    public List<MembershipRow> listByOrg(UUID orgId, int limit) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT m.id, m.user_id, m.org_id, m.status, m.joined_at
                        FROM organization_memberships m
                        WHERE m.org_id = ?
                        ORDER BY m.created_at ASC
                        LIMIT ?
                        """)) {
            ps.setObject(1, orgId);
            ps.setInt(2, limit);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<MembershipRow>();
                while (rs.next()) {
                    var mid = rs.getObject(1, UUID.class);
                    var uid = rs.getObject(2, UUID.class);
                    var oid = rs.getObject(3, UUID.class);
                    var st = rs.getString(4);
                    var jt = rs.getTimestamp(5);
                    var roles = loadRoleKeys(mid);
                    out.add(new MembershipRow(mid, uid, oid, st, roles, jt == null ? null : jt.toInstant()));
                }
                return out;
            }
        }
    }

    public List<OrgSummaryRow> listOrgsForUser(UUID userId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT o.id, o.slug, o.name, m.id
                        FROM organization_memberships m
                        JOIN organizations o ON o.id = m.org_id
                        WHERE m.user_id = ? AND m.status = 'active'
                        ORDER BY o.name
                        """)) {
            ps.setObject(1, userId);
            try (var rs = ps.executeQuery()) {
                var map = new LinkedHashMap<UUID, OrgSummaryRow>();
                while (rs.next()) {
                    var oid = rs.getObject(1, UUID.class);
                    var slug = rs.getString(2);
                    var name = rs.getString(3);
                    var mid = rs.getObject(4, UUID.class);
                    var roles = loadRoleKeys(mid);
                    map.put(oid, new OrgSummaryRow(oid, slug, name, roles));
                }
                return new ArrayList<>(map.values());
            }
        }
    }

    public void deleteMembership(UUID membershipId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("DELETE FROM organization_memberships WHERE id = ?")) {
            ps.setObject(1, membershipId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(UUID membershipId, String status) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE organization_memberships
                        SET status = ?, joined_at = CASE WHEN ? = 'active' AND joined_at IS NULL THEN now() ELSE joined_at END,
                            updated_at = now()
                        WHERE id = ?
                        """)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setObject(3, membershipId);
            ps.executeUpdate();
        }
    }

    public void replaceRoles(UUID membershipId, List<UUID> roleIds) throws SQLException {
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var del = c.prepareStatement("DELETE FROM membership_roles WHERE membership_id = ?")) {
                    del.setObject(1, membershipId);
                    del.executeUpdate();
                }
                try (var ins =
                        c.prepareStatement("INSERT INTO membership_roles (membership_id, role_id) VALUES (?, ?)")) {
                    for (UUID rid : roleIds) {
                        ins.setObject(1, membershipId);
                        ins.setObject(2, rid);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public long countOwnersInOrgExcept(UUID orgId, UUID excludeMembershipId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT count(*) FROM organization_memberships m
                        JOIN membership_roles mr ON mr.membership_id = m.id
                        JOIN roles r ON r.id = mr.role_id
                        WHERE m.org_id = ? AND m.id <> ? AND m.status = 'active' AND r.key = 'owner'
                        """)) {
            ps.setObject(1, orgId);
            ps.setObject(2, excludeMembershipId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public long countOwnersInOrg(UUID orgId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT count(*) FROM organization_memberships m
                        JOIN membership_roles mr ON mr.membership_id = m.id
                        JOIN roles r ON r.id = mr.role_id
                        WHERE m.org_id = ? AND m.status = 'active' AND r.key = 'owner'
                        """)) {
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<String> loadRoleKeys(UUID membershipId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                """
                SELECT r.key FROM membership_roles mr
                JOIN roles r ON r.id = mr.role_id
                WHERE mr.membership_id = ?
                ORDER BY r.key
                """)) {
            ps.setObject(1, membershipId);
            try (var rs = ps.executeQuery()) {
                var keys = new ArrayList<String>();
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
                return keys;
            }
        }
    }

    public void addRole(UUID membershipId, UUID roleId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO membership_roles (membership_id, role_id) VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """)) {
            ps.setObject(1, membershipId);
            ps.setObject(2, roleId);
            ps.executeUpdate();
        }
    }

    public record MembershipRow(
            UUID id, UUID userId, UUID orgId, String status, List<String> roleKeys, Instant joinedAt) {}

    public record OrgSummaryRow(UUID orgId, String slug, String name, List<String> roles) {}
}

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
public final class GroupRepository {

    private final DataSource dataSource;

    public GroupRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID insertDefault(UUID orgId, String orgName) throws SQLException {
        return insert(orgId, orgName, "default", true);
    }

    public UUID insert(UUID orgId, String name, String slug, boolean isDefault) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO organization_groups (org_id, name, slug, is_default, status)
                        VALUES (?, ?, ?, ?, 'active')
                        RETURNING id
                        """)) {
            ps.setObject(1, orgId);
            ps.setString(2, name);
            ps.setString(3, slug);
            ps.setBoolean(4, isDefault);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public Optional<GroupRow> findById(UUID orgId, UUID groupId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, org_id, name, slug, is_default, status, created_at
                        FROM organization_groups
                        WHERE id = ? AND org_id = ?
                        """)) {
            ps.setObject(1, groupId);
            ps.setObject(2, orgId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    public Optional<GroupRow> findDefaultByOrg(UUID orgId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, org_id, name, slug, is_default, status, created_at
                        FROM organization_groups
                        WHERE org_id = ? AND is_default = true
                        LIMIT 1
                        """)) {
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        }
    }

    public List<GroupRow> listForUser(UUID orgId, UUID userId, boolean orgWide, int limit) throws SQLException {
        var sql =
                """
                SELECT g.id, g.org_id, g.name, g.slug, g.is_default, g.status, g.created_at
                FROM organization_groups g
                WHERE g.org_id = ? AND g.status = 'active'
                """;
        if (!orgWide) {
            sql +=
                    """
                     AND EXISTS (
                       SELECT 1 FROM group_memberships gm
                       WHERE gm.group_id = g.id AND gm.user_id = ? AND gm.status = 'active'
                     )
                    """;
        }
        sql += " ORDER BY g.is_default DESC, g.name ASC LIMIT ?";
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(sql)) {
            var idx = 1;
            ps.setObject(idx++, orgId);
            if (!orgWide) {
                ps.setObject(idx++, userId);
            }
            ps.setInt(idx, limit);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<GroupRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        }
    }

    public List<GroupRow> listByOrg(UUID orgId, int limit) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, org_id, name, slug, is_default, status, created_at
                        FROM organization_groups
                        WHERE org_id = ? AND status = 'active'
                        ORDER BY is_default DESC, name ASC
                        LIMIT ?
                        """)) {
            ps.setObject(1, orgId);
            ps.setInt(2, limit);
            try (var rs = ps.executeQuery()) {
                var out = new ArrayList<GroupRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        }
    }

    public UUID insertGroupMembership(UUID orgId, UUID groupId, UUID userId, String status)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO group_memberships (org_id, group_id, user_id, status, joined_at)
                        VALUES (?, ?, ?, ?, CASE WHEN ? = 'active' THEN now() ELSE NULL END)
                        ON CONFLICT (group_id, user_id) DO UPDATE
                        SET status = EXCLUDED.status,
                            joined_at = COALESCE(group_memberships.joined_at, EXCLUDED.joined_at),
                            updated_at = now()
                        RETURNING id
                        """)) {
            ps.setObject(1, orgId);
            ps.setObject(2, groupId);
            ps.setObject(3, userId);
            ps.setString(4, status);
            ps.setString(5, status);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public boolean hasActiveMembership(UUID userId, UUID groupId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT 1 FROM group_memberships
                        WHERE user_id = ? AND group_id = ? AND status = 'active'
                        """)) {
            ps.setObject(1, userId);
            ps.setObject(2, groupId);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void replaceGroupRoles(UUID groupMembershipId, List<UUID> roleIds) throws SQLException {
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (var del = c.prepareStatement("DELETE FROM group_membership_roles WHERE group_membership_id = ?")) {
                    del.setObject(1, groupMembershipId);
                    del.executeUpdate();
                }
                if (!roleIds.isEmpty()) {
                    try (var ins = c.prepareStatement(
                            "INSERT INTO group_membership_roles (group_membership_id, role_id) VALUES (?, ?)")) {
                        for (UUID rid : roleIds) {
                            ins.setObject(1, groupMembershipId);
                            ins.setObject(2, rid);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
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

    private static GroupRow mapRow(java.sql.ResultSet rs) throws SQLException {
        return new GroupRow(
                rs.getObject("id", UUID.class),
                rs.getObject("org_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getBoolean("is_default"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record GroupRow(
            UUID id, UUID orgId, String name, String slug, boolean isDefault, String status, Instant createdAt) {}
}

package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class OrganizationRepository {

    private final DataSource dataSource;

    public OrganizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID insert(String name, String slug, String planTier) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO organizations (name, slug, plan_tier) VALUES (?, ?, ?)
                        RETURNING id
                        """)) {
            ps.setString(1, name);
            ps.setString(2, slug);
            if (planTier == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, planTier);
            }
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public Optional<OrgRow> findById(UUID id) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, name, slug, plan_tier, created_at FROM organizations WHERE id = ?
                        """)) {
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(
                            new OrgRow(
                                    rs.getObject(1, UUID.class),
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    rs.getTimestamp(5).toInstant()));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> findIdBySlugLower(String slugLower) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("SELECT id FROM organizations WHERE lower(slug) = ?")) {
            ps.setString(1, slugLower);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    public void update(UUID id, String name, String slug) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        UPDATE organizations SET
                          name = COALESCE(?, name),
                          slug = COALESCE(?, slug),
                          updated_at = now()
                        WHERE id = ?
                        """)) {
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    public record OrgRow(UUID id, String name, String slug, String planTier, Instant createdAt) {}
}

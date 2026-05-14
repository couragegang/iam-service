package com.couragegang.iam.repo;

import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class IdpRepository {

    private final DataSource dataSource;

    public IdpRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record IdpRow(
            UUID id,
            String type,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String metadataUrl,
            boolean enabled,
            boolean jitProvisioning,
            @Nullable UUID defaultRoleId) {}

    public Optional<IdpRow> findByOrg(UUID orgId) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, type, issuer, client_id, metadata_url, enabled, jit_provisioning, default_role_id
                        FROM org_idp_configs WHERE org_id = ?
                        """)) {
            ps.setObject(1, orgId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new IdpRow(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getBoolean(6),
                                rs.getBoolean(7),
                                rs.getObject(8, UUID.class)));
            }
        }
    }

    public void upsert(
            UUID orgId,
            String type,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String clientSecretEnc,
            @Nullable String metadataUrl,
            boolean enabled,
            boolean jitProvisioning,
            @Nullable UUID defaultRoleId)
            throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO org_idp_configs (org_id, type, issuer, client_id, client_secret_enc, metadata_url, enabled, jit_provisioning, default_role_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (org_id) DO UPDATE SET
                          type = EXCLUDED.type,
                          issuer = EXCLUDED.issuer,
                          client_id = EXCLUDED.client_id,
                          client_secret_enc = COALESCE(EXCLUDED.client_secret_enc, org_idp_configs.client_secret_enc),
                          metadata_url = EXCLUDED.metadata_url,
                          enabled = EXCLUDED.enabled,
                          jit_provisioning = EXCLUDED.jit_provisioning,
                          default_role_id = EXCLUDED.default_role_id,
                          updated_at = now()
                        """)) {
            ps.setObject(1, orgId);
            ps.setString(2, type);
            ps.setString(3, issuer);
            ps.setString(4, clientId);
            if (clientSecretEnc == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, clientSecretEnc);
            }
            ps.setString(6, metadataUrl);
            ps.setBoolean(7, enabled);
            ps.setBoolean(8, jitProvisioning);
            if (defaultRoleId == null) {
                ps.setNull(9, Types.OTHER);
            } else {
                ps.setObject(9, defaultRoleId);
            }
            ps.executeUpdate();
        }
    }
}

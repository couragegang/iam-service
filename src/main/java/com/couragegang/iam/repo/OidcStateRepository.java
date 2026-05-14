package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

@Singleton
public final class OidcStateRepository {

    private final DataSource dataSource;

    public OidcStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(String state, String provider, String redirectAfter, Instant expiresAt) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO oauth_oidc_states (state, provider, redirect_after, expires_at) VALUES (?, ?, ?, ?)
                        """)) {
            ps.setString(1, state);
            ps.setString(2, provider);
            ps.setString(3, redirectAfter);
            ps.setTimestamp(4, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public Optional<OidcStateRow> takeIfValid(String state) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        DELETE FROM oauth_oidc_states WHERE state = ? AND expires_at > now()
                        RETURNING provider, redirect_after
                        """)) {
            ps.setString(1, state);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OidcStateRow(rs.getString(1), rs.getString(2)));
            }
        }
    }

    public record OidcStateRow(String provider, String redirectAfter) {}
}

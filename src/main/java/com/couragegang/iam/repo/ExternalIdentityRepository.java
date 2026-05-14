package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class ExternalIdentityRepository {

    private final DataSource dataSource;

    public ExternalIdentityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UUID> findUserId(String provider, String subject) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        "SELECT user_id FROM user_external_identities WHERE provider = ? AND subject = ?")) {
            ps.setString(1, provider);
            ps.setString(2, subject);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    public void link(UUID userId, String provider, String subject, String emailAtLink) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO user_external_identities (user_id, provider, subject, email_at_link)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (provider, subject) DO NOTHING
                        """)) {
            ps.setObject(1, userId);
            ps.setString(2, provider);
            ps.setString(3, subject);
            ps.setString(4, emailAtLink);
            ps.executeUpdate();
        }
    }
}

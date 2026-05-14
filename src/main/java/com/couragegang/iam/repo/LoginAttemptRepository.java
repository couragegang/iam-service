package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.net.InetAddress;
import java.sql.SQLException;
import java.sql.Types;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;

@Singleton
public final class LoginAttemptRepository {

    private final DataSource dataSource;

    public LoginAttemptRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(String emailLower, InetAddress ip, boolean success) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        INSERT INTO login_attempts (email_lower, ip_inet, success) VALUES (?, ?, ?)
                        """)) {
            ps.setString(1, emailLower);
            if (ip == null) {
                ps.setNull(2, Types.OTHER);
            } else {
                var inet = new PGobject();
                inet.setType("inet");
                inet.setValue(ip.getHostAddress());
                ps.setObject(2, inet);
            }
            ps.setBoolean(3, success);
            ps.executeUpdate();
        }
    }
}

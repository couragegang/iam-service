package com.couragegang.iam.repo;

import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

@Singleton
public final class RoleRepository {

    private final DataSource dataSource;

    public RoleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<UUID> idsByKeys(List<String> keys) throws SQLException {
        if (keys.isEmpty()) {
            return List.of();
        }
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT id, key FROM roles WHERE key = ANY(?::text[])
                        """)) {
            var arr = c.createArrayOf("text", keys.toArray());
            ps.setArray(1, arr);
            try (var rs = ps.executeQuery()) {
                var map = new java.util.HashMap<String, UUID>();
                while (rs.next()) {
                    map.put(rs.getString(2), rs.getObject(1, UUID.class));
                }
                var ordered = new ArrayList<UUID>();
                for (String k : keys) {
                    var id = map.get(k);
                    if (id != null) {
                        ordered.add(id);
                    }
                }
                return ordered;
            }
        }
    }

    public Optional<UUID> idByKey(String key) throws SQLException {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement("SELECT id FROM roles WHERE key = ?")) {
            ps.setString(1, key);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject(1, UUID.class));
                }
            }
        }
        return Optional.empty();
    }

    public List<String> distinctPermissionKeysForRoleKeys(List<String> roleKeys) throws SQLException {
        if (roleKeys.isEmpty()) {
            return List.of();
        }
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        """
                        SELECT DISTINCT p.key FROM roles r
                        JOIN role_permissions rp ON rp.role_id = r.id
                        JOIN permissions p ON p.id = rp.permission_id
                        WHERE r.key = ANY(?::text[])
                        ORDER BY p.key
                        """)) {
            var arr = c.createArrayOf("text", roleKeys.toArray());
            ps.setArray(1, arr);
            try (var rs = ps.executeQuery()) {
                var set = new LinkedHashSet<String>();
                while (rs.next()) {
                    set.add(rs.getString(1));
                }
                return new ArrayList<>(set);
            }
        }
    }
}

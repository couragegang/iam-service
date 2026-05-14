package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couragegang.iam.TestSecrets;
import com.couragegang.iam.config.IamProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class JwtServiceTest {

    private JwtService jwt;

    @BeforeEach
    void setUp() {
        var props = new IamProperties(TestSecrets.JWT_SECRET, 3600, 86_400, null, null, null, null, null, null);
        jwt = new JwtService(props);
    }

    @Test
    void mintAndParseWithoutOrg() throws Exception {
        var uid = UUID.randomUUID();
        var raw = jwt.mintAccess(uid, null, List.of("member", "owner"));
        var p = jwt.parseAndVerify(raw);
        assertThat(p.userId()).isEqualTo(uid);
        assertThat(p.orgId()).isNull();
        assertThat(p.roles()).containsExactly("member", "owner");
        assertThat(p.expiry()).isAfter(Instant.now());
    }

    @Test
    void mintAndParseWithOrg() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var raw = jwt.mintAccess(uid, org, List.of());
        var p = jwt.parseAndVerify(raw);
        assertThat(p.orgId()).isEqualTo(org);
    }

    @Test
    void parseRejectsWrongSignature() throws Exception {
        var other = new JwtService(
                new IamProperties(TestSecrets.JWT_SECRET.replace('a', 'b'), 3600, 86_400, null, null, null, null, null, null));
        var uid = UUID.randomUUID();
        var forged = other.mintAccess(uid, null, List.of());
        assertThatThrownBy(() -> jwt.parseAndVerify(forged)).isInstanceOf(Exception.class);
    }

    @Test
    void parseRejectsGarbage() {
        assertThatThrownBy(() -> jwt.parseAndVerify("not.a.jwt")).isInstanceOf(Exception.class);
    }
}

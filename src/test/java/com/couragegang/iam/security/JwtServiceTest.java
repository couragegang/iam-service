package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couragegang.iam.TestSecrets;
import com.couragegang.iam.config.IamProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class JwtServiceTest {

    private JwtService jwt;
    private byte[] signingKey;

    @BeforeEach
    void setUp() throws Exception {
        var props = new IamProperties(TestSecrets.JWT_SECRET, 3600, 86_400, null, null, null, null, null, null);
        jwt = new JwtService(props);
        signingKey = MessageDigest.getInstance("SHA-256").digest(TestSecrets.JWT_SECRET.getBytes(StandardCharsets.UTF_8));
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
        var other =
                new JwtService(
                        new IamProperties(
                                TestSecrets.JWT_SECRET.replace('a', 'b'), 3600, 86_400, null, null, null, null, null, null));
        var uid = UUID.randomUUID();
        var forged = other.mintAccess(uid, null, List.of());
        assertThatThrownBy(() -> jwt.parseAndVerify(forged)).isInstanceOf(Exception.class);
    }

    @Test
    void parseRejectsGarbage() {
        assertThatThrownBy(() -> jwt.parseAndVerify("not.a.jwt")).isInstanceOf(Exception.class);
    }

    @Test
    void parseIgnoresBlankOrgClaim() throws Exception {
        var uid = UUID.randomUUID();
        var now = Instant.now();
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(uid.toString())
                        .claim("org", "   ")
                        .claim("roles", List.of())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(3600)))
                        .build();
        var raw = sign(claims);
        var p = jwt.parseAndVerify(raw);
        assertThat(p.userId()).isEqualTo(uid);
        assertThat(p.orgId()).isNull();
    }

    @Test
    void parseIgnoresNonListRolesClaim() throws Exception {
        var uid = UUID.randomUUID();
        var now = Instant.now();
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(uid.toString())
                        .claim("roles", "owner")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(3600)))
                        .build();
        var p = jwt.parseAndVerify(sign(claims));
        assertThat(p.userId()).isEqualTo(uid);
        assertThat(p.roles()).isEmpty();
    }

    @Test
    void parseSkipsNullRoleEntries() throws Exception {
        var uid = UUID.randomUUID();
        var now = Instant.now();
        var claims =
                new JWTClaimsSet.Builder()
                        .subject(uid.toString())
                        .claim("roles", Arrays.asList("admin", null, "member"))
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(3600)))
                        .build();
        var p = jwt.parseAndVerify(sign(claims));
        assertThat(p.roles()).containsExactly("admin", "member");
    }

    private String sign(JWTClaimsSet claims) throws Exception {
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner(signingKey));
        return signed.serialize();
    }
}

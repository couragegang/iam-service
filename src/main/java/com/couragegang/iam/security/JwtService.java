package com.couragegang.iam.security;

import com.couragegang.iam.config.IamProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Singleton
public final class JwtService {

    private final IamProperties props;
    private final byte[] signingKey;

    public JwtService(IamProperties props) {
        this.props = props;
        this.signingKey = sha256(props.jwtSecret());
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String mintAccess(UUID userId, UUID orgId, List<String> roles) {
        try {
            var now = Instant.now();
            var exp = now.plusSeconds(props.jwtAccessTtlSeconds());
            var b = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp));
            if (orgId != null) {
                b.claim("org", orgId.toString());
            }
            b.claim("roles", roles);
            var claims = b.build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public ParsedAccess parseAndVerify(String token) throws JOSEException, java.text.ParseException {
        var jwt = SignedJWT.parse(token);
        if (!jwt.verify(new MACVerifier(signingKey))) {
            throw new JOSEException("invalid signature");
        }
        var set = jwt.getJWTClaimsSet();
        var sub = UUID.fromString(set.getSubject());
        UUID org = null;
        var orgClaim = set.getClaim("org");
        if (orgClaim != null && orgClaim.toString() != null && !orgClaim.toString().isBlank()) {
            org = UUID.fromString(orgClaim.toString());
        }
        var rolesClaim = set.getClaim("roles");
        List<String> roles = new ArrayList<>();
        if (rolesClaim instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    roles.add(o.toString());
                }
            }
        }
        return new ParsedAccess(sub, org, roles, set.getExpirationTime().toInstant());
    }

    public record ParsedAccess(UUID userId, UUID orgId, List<String> roles, Instant expiry) {}
}

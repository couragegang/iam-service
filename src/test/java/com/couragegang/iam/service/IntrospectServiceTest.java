package com.couragegang.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.couragegang.iam.TestSecrets;
import com.couragegang.iam.api.dto.InternalModels.IntrospectRequest;
import com.couragegang.iam.config.IamProperties;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class IntrospectServiceTest {

    JwtService jwt;

    @Mock
    RoleRepository roles;

    IntrospectService svc;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(new IamProperties(TestSecrets.JWT_SECRET, 900, 3600, null, null, null, null, null, null));
        svc = new IntrospectService(jwt, roles);
    }

    @Test
    void invalidLiteralReturnsInactive() {
        var r = svc.introspect(new IntrospectRequest("invalid"));
        assertThat(r.active()).isFalse();
        assertThat(r.sub()).isNull();
    }

    @Test
    void validTokenReturnsActiveAndPerms() throws Exception {
        var uid = UUID.randomUUID();
        var raw = jwt.mintAccess(uid, null, List.of("owner"));
        when(roles.distinctPermissionKeysForRoleKeys(List.of("owner"))).thenReturn(List.of("iam.org.read"));
        var r = svc.introspect(new IntrospectRequest(raw));
        assertThat(r.active()).isTrue();
        assertThat(r.sub()).isEqualTo(uid);
        assertThat(r.permissions()).containsExactly("iam.org.read");
    }

    @Test
    void badJwtReturnsInactive() {
        var r = svc.introspect(new IntrospectRequest("nope"));
        assertThat(r.active()).isFalse();
    }

    @Test
    void validTokenWithOrgIncludesOrgId() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var raw = jwt.mintAccess(uid, org, List.of("member"));
        when(roles.distinctPermissionKeysForRoleKeys(List.of("member"))).thenReturn(List.of("iam.org.read"));
        var r = svc.introspect(new IntrospectRequest(raw));
        assertThat(r.active()).isTrue();
        assertThat(r.orgId()).isEqualTo(org);
    }
}

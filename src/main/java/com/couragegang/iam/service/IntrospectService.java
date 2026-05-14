package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.InternalModels.IntrospectRequest;
import com.couragegang.iam.api.dto.InternalModels.IntrospectResponse;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.security.JwtService;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class IntrospectService {

    private final JwtService jwt;
    private final RoleRepository roles;

    public IntrospectService(JwtService jwt, RoleRepository roles) {
        this.jwt = jwt;
        this.roles = roles;
    }

    public IntrospectResponse introspect(IntrospectRequest req) {
        try {
            if ("invalid".equals(req.token())) {
                return inactive();
            }
            var p = jwt.parseAndVerify(req.token());
            List<String> perms = roles.distinctPermissionKeysForRoleKeys(p.roles());
            return new IntrospectResponse(
                    true,
                    p.userId(),
                    p.orgId(),
                    "openid",
                    p.roles(),
                    perms,
                    p.expiry().getEpochSecond());
        } catch (Exception e) {
            return inactive();
        }
    }

    private static IntrospectResponse inactive() {
        return new IntrospectResponse(false, null, null, null, null, null, null);
    }
}

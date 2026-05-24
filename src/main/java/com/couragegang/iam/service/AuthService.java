package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.AuthModels.AuthTokensResponse;
import com.couragegang.iam.api.dto.AuthModels.ForgotPasswordRequest;
import com.couragegang.iam.api.dto.AuthModels.LoginRequest;
import com.couragegang.iam.api.dto.AuthModels.RefreshRequest;
import com.couragegang.iam.api.dto.AuthModels.RegisterRequest;
import com.couragegang.iam.api.dto.AuthModels.ResetPasswordRequest;
import com.couragegang.iam.api.dto.AuthModels.SwitchOrgRequest;
import com.couragegang.iam.api.dto.AuthModels.VerifyEmailRequest;
import com.couragegang.iam.config.IamProperties;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.integration.ConfigWorkspaceClient;
import com.couragegang.iam.repo.GroupRepository;
import com.couragegang.iam.repo.LoginAttemptRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.OrganizationRepository;
import com.couragegang.iam.repo.RefreshSessionRepository;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.repo.TokenRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.HexSha256;
import com.couragegang.iam.security.JwtService;
import com.couragegang.iam.security.PasswordHasher;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Singleton
public final class AuthService {

    private static final SecureRandom RND = new SecureRandom();

    private final IamProperties props;
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final JwtService jwt;
    private final RefreshSessionRepository sessions;
    private final TokenRepository tokens;
    private final OrganizationRepository orgs;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final RoleRepository roles;
    private final LoginAttemptRepository loginAttempts;
    private final ConfigWorkspaceClient configWorkspaces;

    public AuthService(
            IamProperties props,
            UserRepository users,
            PasswordHasher passwords,
            JwtService jwt,
            RefreshSessionRepository sessions,
            TokenRepository tokens,
            OrganizationRepository orgs,
            GroupRepository groups,
            MembershipRepository memberships,
            RoleRepository roles,
            LoginAttemptRepository loginAttempts,
            ConfigWorkspaceClient configWorkspaces) {
        this.props = props;
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.sessions = sessions;
        this.tokens = tokens;
        this.orgs = orgs;
        this.groups = groups;
        this.memberships = memberships;
        this.roles = roles;
        this.loginAttempts = loginAttempts;
        this.configWorkspaces = configWorkspaces;
    }

    public AuthTokensResponse register(RegisterRequest req) {
        try {
            var email = req.email().trim().toLowerCase(Locale.ROOT);
            if (users.findActiveIdByEmailLower(email).isPresent()) {
                throw new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "email already registered");
            }
            var userId = users.insertUser(email, req.displayName(), "ru");
            users.insertPassword(userId, passwords.hash(req.password()));
            var rawVerify = randomUrlToken();
            tokens.insertEmailVerification(userId, HexSha256.hashUtf8(rawVerify), Instant.now().plus(2, ChronoUnit.DAYS));
            var orgName = resolveRegistrationOrgName(req);
            var orgId = provisionNewOrganization(userId, orgName);
            return issueTokens(userId, orgId, null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthTokensResponse login(LoginRequest req, InetAddress clientIp) {
        try {
            var email = req.email().trim().toLowerCase(Locale.ROOT);
            var uidOpt = users.findActiveIdByEmailLower(email);
            if (uidOpt.isEmpty()) {
                loginAttempts.insert(email, clientIp, false);
                throw new IamApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "invalid credentials");
            }
            var uid = uidOpt.get();
            var hashOpt = users.findPasswordHash(uid);
            if (hashOpt.isEmpty() || !passwords.matches(req.password(), hashOpt.get())) {
                loginAttempts.insert(email, clientIp, false);
                throw new IamApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "invalid credentials");
            }
            loginAttempts.insert(email, clientIp, true);
            return issueTokens(uid, resolveDefaultOrgId(uid), null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthTokensResponse refresh(RefreshRequest req) {
        try {
            var h = HexSha256.hashUtf8(req.refreshToken());
            var rowOpt = sessions.findActiveByRefreshHash(h);
            if (rowOpt.isEmpty()) {
                throw new IamApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "invalid refresh token");
            }
            var row = rowOpt.get();
            sessions.revoke(row.id());
            return issueTokens(row.userId(), row.orgId(), row.familyId());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void logout(RefreshRequest req) {
        try {
            var h = HexSha256.hashUtf8(req.refreshToken());
            sessions.findActiveByRefreshHash(h).ifPresent(r -> {
                try {
                    sessions.revoke(r.id());
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void forgot(ForgotPasswordRequest req) {
        try {
            var email = req.email().trim().toLowerCase(Locale.ROOT);
            users.findActiveIdByEmailLower(email).ifPresent(uid -> {
                try {
                    var raw = randomUrlToken();
                    tokens.insertPasswordReset(uid, HexSha256.hashUtf8(raw), Instant.now().plus(1, ChronoUnit.HOURS));
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void reset(ResetPasswordRequest req) {
        try {
            var h = HexSha256.hashUtf8(req.token());
            var uidOpt = tokens.consumePasswordReset(h);
            if (uidOpt.isEmpty()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid or expired token");
            }
            var uid = uidOpt.get();
            users.updatePasswordHash(uid, passwords.hash(req.newPassword()));
            sessions.revokeAllForUser(uid);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void verify(VerifyEmailRequest req) {
        try {
            var h = HexSha256.hashUtf8(req.token());
            var uidOpt = tokens.consumeEmailVerification(h);
            if (uidOpt.isEmpty()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid or expired token");
            }
            users.setEmailVerified(uidOpt.get(), Instant.now());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthTokensResponse switchOrg(SwitchOrgRequest req, UUID userId) {
        try {
            var m = memberships
                    .findByUserAndOrg(userId, req.orgId())
                    .orElseThrow(() -> new IamApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "not a member"));
            if (!"active".equals(m.status())) {
                throw new IamApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "membership not active");
            }
            return issueTokens(userId, req.orgId(), null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public AuthTokensResponse issueSession(UUID userId, UUID orgId) {
        try {
            return issueTokens(userId, orgId, null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private AuthTokensResponse issueTokens(UUID userId, UUID orgId, UUID reuseFamily) throws SQLException {
        List<String> roleKeys = List.of();
        if (orgId != null) {
            var m = memberships.findByUserAndOrg(userId, orgId);
            if (m.isPresent()) {
                roleKeys = m.get().roleKeys();
            }
        }
        var access = jwt.mintAccess(userId, orgId, roleKeys);
        var refreshPlain = randomUrlToken();
        var refreshHash = HexSha256.hashUtf8(refreshPlain);
        var family = reuseFamily != null ? reuseFamily : UUID.randomUUID();
        var jti = UUID.randomUUID().toString();
        var exp = Instant.now().plusSeconds(props.refreshTtlSeconds());
        sessions.insert(userId, orgId, family, jti, exp, refreshHash);
        return new AuthTokensResponse(access, props.jwtAccessTtlSeconds(), refreshPlain, props.refreshTtlSeconds(), "Bearer");
    }

    private static String randomUrlToken() {
        var buf = new byte[32];
        RND.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private UUID resolveDefaultOrgId(UUID userId) throws SQLException {
        var orgs = memberships.listOrgsForUser(userId);
        return orgs.isEmpty() ? null : orgs.getFirst().orgId();
    }

    private static String resolveRegistrationOrgName(RegisterRequest req) {
        if (req.organizationName() != null && !req.organizationName().isBlank()) {
            return req.organizationName().trim();
        }
        var fromDisplay = req.displayName().trim();
        if (!fromDisplay.isBlank()) {
            return fromDisplay;
        }
        var local = req.email().trim().toLowerCase(Locale.ROOT).split("@")[0];
        return local.isBlank() ? "Organization" : local;
    }

    private UUID provisionNewOrganization(UUID userId, String orgName) throws SQLException {
        var slug = uniqueSlug(slugify(orgName));
        var orgId = orgs.insert(orgName, slug, null);
        var defaultGroupId = groups.insertDefault(orgId, orgName);
        configWorkspaces.bootstrapDefaultWorkspace(orgId, defaultGroupId, orgName);
        var memId = memberships.insert(userId, orgId, "active", "org_wide");
        var ownerId = roles.idByKey("owner").orElseThrow(() -> new IllegalStateException("owner role missing"));
        memberships.addRole(memId, ownerId);
        return orgId;
    }

    private static String slugify(String name) {
        var s = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return s.isEmpty() ? "org" : s;
    }

    private String uniqueSlug(String base) throws SQLException {
        var candidate = base;
        for (int i = 0; i < 20; i++) {
            if (orgs.findIdBySlugLower(candidate).isEmpty()) {
                return candidate;
            }
            candidate = base + "-" + (i + 1);
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

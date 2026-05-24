package com.couragegang.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import com.couragegang.iam.TestSecrets;
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
import com.couragegang.iam.security.JwtService;
import com.couragegang.iam.security.PasswordHasher;
import io.micronaut.http.HttpStatus;
import java.net.InetAddress;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AuthServiceTest {

    @Mock
    UserRepository users;

    @Mock
    PasswordHasher passwords;

    @Mock
    JwtService jwt;

    @Mock
    RefreshSessionRepository sessions;

    @Mock
    TokenRepository tokens;

    @Mock
    OrganizationRepository orgs;

    @Mock
    GroupRepository groups;

    @Mock
    MembershipRepository memberships;

    @Mock
    RoleRepository roles;

    @Mock
    LoginAttemptRepository loginAttempts;

    @Mock
    ConfigWorkspaceClient configWorkspaces;

    IamProperties props;
    AuthService auth;

    @BeforeEach
    void setUp() {
        props = new IamProperties(TestSecrets.JWT_SECRET, 900, 3600, null, null, null, null, null, null);
        lenient()
                .when(configWorkspaces.bootstrapDefaultWorkspace(any(), any(), anyString()))
                .thenReturn(Optional.empty());
        auth = new AuthService(
                props,
                users,
                passwords,
                jwt,
                sessions,
                tokens,
                orgs,
                groups,
                memberships,
                roles,
                loginAttempts,
                configWorkspaces);
    }

    @Test
    void registerConflictWhenEmailExists() throws Exception {
        when(users.findActiveIdByEmailLower("a@b.co")).thenReturn(Optional.of(UUID.randomUUID()));
        var req = new RegisterRequest("A@B.co", "password-long-1", "Name", null);
        assertThatThrownBy(() -> auth.register(req))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.CONFLICT);
    }

    @Test
    void registerWithoutOrgNameCreatesOrgFromDisplayName() throws Exception {
        var uid = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var memId = UUID.randomUUID();
        var ownerRole = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("u@x.co")).thenReturn(Optional.empty());
        when(users.insertUser(eq("u@x.co"), eq("N"), eq("ru"))).thenReturn(uid);
        when(passwords.hash("pw-long-12")).thenReturn("hash");
        doNothing().when(tokens).insertEmailVerification(any(), anyString(), any());
        when(orgs.findIdBySlugLower("n")).thenReturn(Optional.empty());
        when(orgs.insert(eq("N"), eq("n"), eq(null))).thenReturn(orgId);
        when(groups.insertDefault(orgId, "N")).thenReturn(UUID.randomUUID());
        when(memberships.insert(uid, orgId, "active", "org_wide")).thenReturn(memId);
        when(roles.idByKey("owner")).thenReturn(Optional.of(ownerRole));
        when(jwt.mintAccess(eq(uid), eq(orgId), eq(List.of()))).thenReturn("access");
        when(sessions.insert(any(), eq(orgId), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, orgId, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        var req = new RegisterRequest("U@x.co", "pw-long-12", "N", null);
        var tok = auth.register(req);
        assertThat(tok.accessToken()).isEqualTo("access");
        verify(orgs).insert(eq("N"), eq("n"), eq(null));
        verify(configWorkspaces).bootstrapDefaultWorkspace(eq(orgId), any(), eq("N"));
    }

    @Test
    void registerWithOrg() throws Exception {
        var uid = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        var memId = UUID.randomUUID();
        var ownerRole = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("o@x.co")).thenReturn(Optional.empty());
        when(users.insertUser(eq("o@x.co"), eq("N"), eq("ru"))).thenReturn(uid);
        when(passwords.hash(anyString())).thenReturn("h");
        doNothing().when(tokens).insertEmailVerification(any(), anyString(), any());
        when(orgs.findIdBySlugLower("acme")).thenReturn(Optional.empty());
        when(orgs.insert(eq("Acme"), eq("acme"), eq(null))).thenReturn(orgId);
        when(groups.insertDefault(orgId, "Acme")).thenReturn(UUID.randomUUID());
        when(memberships.insert(uid, orgId, "active", "org_wide")).thenReturn(memId);
        when(roles.idByKey("owner")).thenReturn(Optional.of(ownerRole));
        when(jwt.mintAccess(eq(uid), eq(orgId), eq(List.of()))).thenReturn("jwt");
        when(sessions.insert(any(), eq(orgId), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, orgId, UUID.randomUUID(), Instant.now(), Instant.now(), "x"));
        var req = new RegisterRequest("O@x.co", "pw-long-12", "N", "Acme");
        var out = auth.register(req);
        assertThat(out.accessToken()).isEqualTo("jwt");
        verify(configWorkspaces).bootstrapDefaultWorkspace(eq(orgId), any(), eq("Acme"));
        verify(memberships).addRole(any(), eq(ownerRole));
    }

    @Test
    void registerOwnerRoleMissingThrows() throws Exception {
        var uid = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("o@x.co")).thenReturn(Optional.empty());
        when(users.insertUser(anyString(), anyString(), eq("ru"))).thenReturn(uid);
        when(passwords.hash(anyString())).thenReturn("h");
        doNothing().when(tokens).insertEmailVerification(any(), anyString(), any());
        when(orgs.findIdBySlugLower("acme")).thenReturn(Optional.empty());
        when(orgs.insert(anyString(), anyString(), eq(null))).thenReturn(orgId);
        when(groups.insertDefault(orgId, "Acme")).thenReturn(UUID.randomUUID());
        when(memberships.insert(uid, orgId, "active", "org_wide")).thenReturn(UUID.randomUUID());
        when(roles.idByKey("owner")).thenReturn(Optional.empty());
        var req = new RegisterRequest("O@x.co", "pw-long-12", "N", "Acme");
        assertThatThrownBy(() -> auth.register(req)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loginUnknownUser() throws Exception {
        when(users.findActiveIdByEmailLower("x@y.co")).thenReturn(Optional.empty());
        doNothing().when(loginAttempts).insert(eq("x@y.co"), any(), eq(false));
        var req = new LoginRequest("X@y.co", "pw");
        assertThatThrownBy(() -> auth.login(req, InetAddress.getLoopbackAddress()))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginBadPassword() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("x@y.co")).thenReturn(Optional.of(uid));
        when(users.findPasswordHash(uid)).thenReturn(Optional.of("hash"));
        when(passwords.matches("bad", "hash")).thenReturn(false);
        doNothing().when(loginAttempts).insert(eq("x@y.co"), any(), eq(false));
        assertThatThrownBy(() -> auth.login(new LoginRequest("x@y.co", "bad"), InetAddress.getLoopbackAddress()))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void loginPicksDefaultOrg() throws Exception {
        var uid = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("m@y.co")).thenReturn(Optional.of(uid));
        when(users.findPasswordHash(uid)).thenReturn(Optional.of("hash"));
        when(passwords.matches("good", "hash")).thenReturn(true);
        doNothing().when(loginAttempts).insert(eq("m@y.co"), any(), eq(true));
        when(memberships.listOrgsForUser(uid))
                .thenReturn(List.of(new MembershipRepository.OrgSummaryRow(orgId, "acme", "Acme", List.of("owner"))));
        when(memberships.findByUserAndOrg(uid, orgId))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), uid, orgId, "active", "org_wide", List.of("owner"), Instant.now())));
        when(jwt.mintAccess(eq(uid), eq(orgId), eq(List.of("owner")))).thenReturn("a");
        when(sessions.insert(any(), eq(orgId), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, orgId, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        auth.login(new LoginRequest("m@y.co", "good"), InetAddress.getLoopbackAddress());
        verify(jwt).mintAccess(eq(uid), eq(orgId), eq(List.of("owner")));
    }

    @Test
    void loginSuccess() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("ok@y.co")).thenReturn(Optional.of(uid));
        when(users.findPasswordHash(uid)).thenReturn(Optional.of("hash"));
        when(passwords.matches("good", "hash")).thenReturn(true);
        doNothing().when(loginAttempts).insert(eq("ok@y.co"), any(), eq(true));
        when(memberships.listOrgsForUser(uid)).thenReturn(List.of());
        when(jwt.mintAccess(eq(uid), eq(null), eq(List.of()))).thenReturn("a");
        when(sessions.insert(any(), eq(null), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, null, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        var out = auth.login(new LoginRequest("ok@y.co", "good"), InetAddress.getLoopbackAddress());
        assertThat(out.accessToken()).isEqualTo("a");
    }

    @Test
    void loginNoPasswordRow() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("z@y.co")).thenReturn(Optional.of(uid));
        when(users.findPasswordHash(uid)).thenReturn(Optional.empty());
        doNothing().when(loginAttempts).insert(anyString(), any(), eq(false));
        assertThatThrownBy(() -> auth.login(new LoginRequest("z@y.co", "pw"), InetAddress.getLoopbackAddress()))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void refreshInvalid() throws Exception {
        when(sessions.findActiveByRefreshHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest("rt")))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshSuccessReusesFamily() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var fam = UUID.randomUUID();
        var sid = UUID.randomUUID();
        var row = new RefreshSessionRepository.SessionRow(sid, uid, org, fam, Instant.now(), Instant.now(), "rh");
        when(sessions.findActiveByRefreshHash(anyString())).thenReturn(Optional.of(row));
        doNothing().when(sessions).revoke(sid);
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), uid, org, "active", "org_wide", List.of("member"), Instant.now())));
        when(jwt.mintAccess(eq(uid), eq(org), eq(List.of("member")))).thenReturn("acc");
        when(sessions.insert(eq(uid), eq(org), eq(fam), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, org, fam, Instant.now(), Instant.now(), "h"));
        var out = auth.refresh(new RefreshRequest("rt"));
        assertThat(out.accessToken()).isEqualTo("acc");
    }

    @Test
    void refreshIssueTokensMembershipAbsent() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var fam = UUID.randomUUID();
        var sid = UUID.randomUUID();
        var row = new RefreshSessionRepository.SessionRow(sid, uid, org, fam, Instant.now(), Instant.now(), "rh");
        when(sessions.findActiveByRefreshHash(anyString())).thenReturn(Optional.of(row));
        doNothing().when(sessions).revoke(sid);
        when(memberships.findByUserAndOrg(uid, org)).thenReturn(Optional.empty());
        when(jwt.mintAccess(eq(uid), eq(org), eq(List.of()))).thenReturn("acc2");
        when(sessions.insert(any(), eq(org), eq(fam), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, org, fam, Instant.now(), Instant.now(), "h"));
        auth.refresh(new RefreshRequest("rt"));
        verify(jwt).mintAccess(uid, org, List.of());
    }

    @Test
    void logoutNoopWhenUnknownRefresh() throws Exception {
        when(sessions.findActiveByRefreshHash(anyString())).thenReturn(Optional.empty());
        auth.logout(new RefreshRequest("x"));
        verify(sessions, never()).revoke(any());
    }

    @Test
    void logoutRevokes() throws Exception {
        var sid = UUID.randomUUID();
        var row = new RefreshSessionRepository.SessionRow(
                sid, UUID.randomUUID(), null, UUID.randomUUID(), Instant.now(), Instant.now(), "h");
        when(sessions.findActiveByRefreshHash(anyString())).thenReturn(Optional.of(row));
        doNothing().when(sessions).revoke(sid);
        auth.logout(new RefreshRequest("tok"));
        verify(sessions).revoke(sid);
    }

    @Test
    void forgotWhenUserMissing() throws Exception {
        when(users.findActiveIdByEmailLower("n@x.co")).thenReturn(Optional.empty());
        auth.forgot(new ForgotPasswordRequest("N@x.co"));
        verify(tokens, never()).insertPasswordReset(any(), anyString(), any());
    }

    @Test
    void forgotWhenUserExists() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("e@x.co")).thenReturn(Optional.of(uid));
        doNothing().when(tokens).insertPasswordReset(any(), anyString(), any());
        auth.forgot(new ForgotPasswordRequest("e@x.co"));
        verify(tokens).insertPasswordReset(eq(uid), anyString(), any());
    }

    @Test
    void resetInvalidToken() throws Exception {
        when(tokens.consumePasswordReset(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.reset(new ResetPasswordRequest("bad", "new-long-12")))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void resetSuccess() throws Exception {
        var uid = UUID.randomUUID();
        when(tokens.consumePasswordReset(anyString())).thenReturn(Optional.of(uid));
        when(passwords.hash("new-long-12")).thenReturn("nh");
        doNothing().when(users).updatePasswordHash(uid, "nh");
        doNothing().when(sessions).revokeAllForUser(uid);
        auth.reset(new ResetPasswordRequest("t", "new-long-12"));
        verify(sessions).revokeAllForUser(uid);
    }

    @Test
    void verifyInvalid() throws Exception {
        when(tokens.consumeEmailVerification(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.verify(new VerifyEmailRequest("bad"))).isInstanceOf(IamApiException.class);
    }

    @Test
    void verifySuccess() throws Exception {
        var uid = UUID.randomUUID();
        when(tokens.consumeEmailVerification(anyString())).thenReturn(Optional.of(uid));
        doNothing().when(users).setEmailVerified(any(), any());
        auth.verify(new VerifyEmailRequest("ok"));
        verify(users).setEmailVerified(eq(uid), any());
    }

    @Test
    void switchOrgNotMember() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        when(memberships.findByUserAndOrg(uid, org)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.switchOrg(new SwitchOrgRequest(org), uid)).isInstanceOf(IamApiException.class);
    }

    @Test
    void switchOrgInactive() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), uid, org, "suspended", "org_wide", List.of(), Instant.now())));
        assertThatThrownBy(() -> auth.switchOrg(new SwitchOrgRequest(org), uid)).isInstanceOf(IamApiException.class);
    }

    @Test
    void switchOrgSuccess() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), uid, org, "active", "org_wide", List.of("owner"), Instant.now())));
        when(jwt.mintAccess(eq(uid), eq(org), eq(List.of("owner")))).thenReturn("j");
        when(sessions.insert(eq(uid), eq(org), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, org, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        var out = auth.switchOrg(new SwitchOrgRequest(org), uid);
        assertThat(out.accessToken()).isEqualTo("j");
    }

    @Test
    void issueSessionDelegates() throws Exception {
        var uid = UUID.randomUUID();
        when(jwt.mintAccess(eq(uid), eq(null), eq(List.of()))).thenReturn("jwt");
        when(sessions.insert(any(), eq(null), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, null, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        assertThat(auth.issueSession(uid, null).accessToken()).isEqualTo("jwt");
    }

    @Test
    void registerSqlExceptionWrapped() throws Exception {
        when(users.findActiveIdByEmailLower(anyString())).thenThrow(new SQLException("db"));
        assertThatThrownBy(() -> auth.register(new RegisterRequest("a@b.co", "pw-long-12", "N", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void uniqueSlugRetries() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findActiveIdByEmailLower(anyString())).thenReturn(Optional.empty());
        when(users.insertUser(anyString(), anyString(), anyString())).thenReturn(uid);
        when(passwords.hash(anyString())).thenReturn("h");
        doNothing().when(tokens).insertEmailVerification(any(), anyString(), any());
        when(orgs.findIdBySlugLower("acme")).thenReturn(Optional.of(UUID.randomUUID()));
        when(orgs.findIdBySlugLower("acme-1")).thenReturn(Optional.empty());
        when(orgs.insert(eq("Acme"), eq("acme-1"), eq(null))).thenReturn(UUID.randomUUID());
        when(memberships.insert(any(), any(), eq("active"), eq("org_wide"))).thenReturn(UUID.randomUUID());
        when(roles.idByKey("owner")).thenReturn(Optional.of(UUID.randomUUID()));
        lenient()
                .when(sessions.insert(any(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        lenient().when(jwt.mintAccess(any(), any(), any())).thenReturn("j");
        auth.register(new RegisterRequest("u@z.co", "pw-long-12", "N", "Acme"));
        verify(orgs).insert(eq("Acme"), eq("acme-1"), eq(null));
    }

    @Test
    void registerBlankOrgNameUsesDisplayName() throws Exception {
        var uid = UUID.randomUUID();
        var orgId = UUID.randomUUID();
        when(users.findActiveIdByEmailLower("b@z.co")).thenReturn(Optional.empty());
        when(users.insertUser(eq("b@z.co"), eq("N"), eq("ru"))).thenReturn(uid);
        when(passwords.hash(anyString())).thenReturn("h");
        doNothing().when(tokens).insertEmailVerification(any(), anyString(), any());
        when(orgs.findIdBySlugLower("n")).thenReturn(Optional.empty());
        when(orgs.insert(eq("N"), eq("n"), eq(null))).thenReturn(orgId);
        when(groups.insertDefault(orgId, "N")).thenReturn(UUID.randomUUID());
        when(memberships.insert(uid, orgId, "active", "org_wide")).thenReturn(UUID.randomUUID());
        when(roles.idByKey("owner")).thenReturn(Optional.of(UUID.randomUUID()));
        when(jwt.mintAccess(eq(uid), eq(orgId), eq(List.of()))).thenReturn("j");
        when(sessions.insert(any(), eq(orgId), any(), anyString(), any(), anyString()))
                .thenReturn(new RefreshSessionRepository.SessionRow(
                        UUID.randomUUID(), uid, orgId, UUID.randomUUID(), Instant.now(), Instant.now(), "h"));
        auth.register(new RegisterRequest("B@z.co", "pw-long-12", "N", "   "));
        verify(orgs).insert(eq("N"), eq("n"), eq(null));
    }
}

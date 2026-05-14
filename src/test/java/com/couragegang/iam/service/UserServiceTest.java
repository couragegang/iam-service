package com.couragegang.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.api.dto.UserModels.ChangePasswordRequest;
import com.couragegang.iam.api.dto.UserModels.MePatchRequest;
import com.couragegang.iam.api.dto.UserModels.MeResponse;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.RefreshSessionRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.PasswordHasher;
import io.micronaut.http.HttpStatus;
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
final class UserServiceTest {

    @Mock
    UserRepository users;

    @Mock
    MembershipRepository memberships;

    @Mock
    PasswordHasher passwords;

    @Mock
    RefreshSessionRepository sessions;

    UserService svc;

    @BeforeEach
    void setUp() {
        svc = new UserService(users, memberships, passwords, sessions);
    }

    @Test
    void meSuccess() throws Exception {
        var uid = UUID.randomUUID();
        var row = new UserRepository.UserRow(uid, "a@b.co", Instant.now(), "active", "N", "ru");
        when(users.findById(uid)).thenReturn(Optional.of(row));
        when(memberships.listOrgsForUser(uid))
                .thenReturn(List.of(new MembershipRepository.OrgSummaryRow(
                        UUID.randomUUID(), "slug", "Org", List.of("owner"))));
        MeResponse me = svc.me(uid);
        assertThat(me.user().email()).isEqualTo("a@b.co");
        assertThat(me.organizations()).hasSize(1);
    }

    @Test
    void meUserMissing() throws Exception {
        when(users.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.me(UUID.randomUUID()))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.NOT_FOUND);
    }

    @Test
    void patchReturnsPublic() throws Exception {
        var uid = UUID.randomUUID();
        doNothing().when(users).updateProfile(uid, "X", "en");
        var row = new UserRepository.UserRow(uid, "a@b.co", null, "active", "X", "en");
        when(users.findById(uid)).thenReturn(Optional.of(row));
        var pub = svc.patch(uid, new MePatchRequest("X", "en"));
        assertThat(pub.displayName()).isEqualTo("X");
    }

    @Test
    void changePasswordWrongCurrent() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findPasswordHash(uid)).thenReturn(Optional.of("h"));
        when(passwords.matches("bad", "h")).thenReturn(false);
        assertThatThrownBy(() -> svc.changePassword(uid, new ChangePasswordRequest("bad", "new-long-12")))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void changePasswordSuccess() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findPasswordHash(uid)).thenReturn(Optional.of("h"));
        when(passwords.matches("cur", "h")).thenReturn(true);
        when(passwords.hash("new-long-12")).thenReturn("nh");
        doNothing().when(users).updatePasswordHash(uid, "nh");
        doNothing().when(sessions).revokeAllForUser(uid);
        svc.changePassword(uid, new ChangePasswordRequest("cur", "new-long-12"));
        verify(sessions).revokeAllForUser(uid);
    }

    @Test
    void listSessions() throws Exception {
        var uid = UUID.randomUUID();
        var sid = UUID.randomUUID();
        when(sessions.listActiveByUser(uid))
                .thenReturn(List.of(new RefreshSessionRepository.SessionListRow(
                        sid, Instant.now(), Instant.now().plusSeconds(10), null, "dev")));
        var list = svc.listSessions(uid);
        assertThat(list.items()).hasSize(1);
    }

    @Test
    void revokeSessionNotFound() throws Exception {
        when(sessions.revokeIfOwned(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> svc.revokeSession(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void revokeSessionOk() throws Exception {
        var uid = UUID.randomUUID();
        var sid = UUID.randomUUID();
        when(sessions.revokeIfOwned(sid, uid)).thenReturn(true);
        svc.revokeSession(uid, sid);
        verify(sessions).revokeIfOwned(sid, uid);
    }

    @Test
    void meSqlWrapped() throws Exception {
        when(users.findById(any())).thenThrow(new SQLException("db"));
        assertThatThrownBy(() -> svc.me(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }
}

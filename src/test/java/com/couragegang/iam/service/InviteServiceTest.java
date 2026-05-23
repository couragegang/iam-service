package com.couragegang.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.api.dto.OrgModels.InviteAcceptRequest;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.repo.GroupRepository;
import com.couragegang.iam.repo.InviteRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.repo.UserRepository;
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
final class InviteServiceTest {

    @Mock
    InviteRepository invites;

    @Mock
    UserRepository users;

    @Mock
    MembershipRepository memberships;

    @Mock
    GroupRepository groups;

    @Mock
    RoleRepository roles;

    InviteService svc;

    @BeforeEach
    void setUp() {
        svc = new InviteService(invites, users, memberships, groups, roles);
    }

    @Test
    void acceptSuccess() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var memId = UUID.randomUUID();
        var user = new UserRepository.UserRow(uid, "Inv@x.co", null, "active", "I", "ru");
        when(users.findById(uid)).thenReturn(Optional.of(user));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(Optional.of(new InviteRepository.InviteAcceptData(
                        UUID.randomUUID(), "inv@x.co", null, List.of("member"), List.of())));
        when(memberships.findByUserAndOrg(uid, org)).thenReturn(Optional.empty());
        when(memberships.insert(uid, org, "active", "org_wide")).thenReturn(memId);
        when(roles.idsByKeys(List.of("member"))).thenReturn(List.of(UUID.randomUUID()));
        doNothing().when(memberships).replaceRoles(eq(memId), any());
        doNothing().when(invites).markAccepted(any());
        when(memberships.findMembership(org, memId))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        memId, uid, org, "active", "org_wide", List.of("member"), Instant.now())));
        Membership m = svc.accept(uid, new InviteAcceptRequest(org, "raw-token"));
        assertThat(m.orgId()).isEqualTo(org);
        verify(invites).markAccepted(any());
    }

    @Test
    void acceptInvalidInvite() throws Exception {
        var uid = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(any(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.accept(uid, new InviteAcceptRequest(UUID.randomUUID(), "t")))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptEmailMismatch() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(Optional.of(new InviteRepository.InviteAcceptData(
                        UUID.randomUUID(), "other@b.co", null, List.of("member"), List.of())));
        assertThatThrownBy(() -> svc.accept(uid, new InviteAcceptRequest(org, "tok")))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.FORBIDDEN);
    }

    @Test
    void acceptAlreadyMember() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(Optional.of(new InviteRepository.InviteAcceptData(
                        UUID.randomUUID(), "a@b.co", null, List.of("member"), List.of())));
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), uid, org, "active", "org_wide", List.of("member"), Instant.now())));
        assertThatThrownBy(() -> svc.accept(uid, new InviteAcceptRequest(org, "tok")))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.CONFLICT);
    }

    @Test
    void acceptGroupScopedInvite() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var gid = UUID.randomUUID();
        var memId = UUID.randomUUID();
        var groupMemId = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(
                        Optional.of(
                                new InviteRepository.InviteAcceptData(
                                        UUID.randomUUID(), "a@b.co", gid, List.of("member"), List.of("editor"))));
        when(memberships.findByUserAndOrg(uid, org)).thenReturn(Optional.empty());
        when(groups.findById(org, gid))
                .thenReturn(
                        Optional.of(
                                new GroupRepository.GroupRow(
                                        gid, org, "G", "g", false, "active", Instant.now())));
        when(memberships.insert(uid, org, "active", "group_scoped")).thenReturn(memId);
        when(roles.idsByKeys(List.of("member"))).thenReturn(List.of(UUID.randomUUID()));
        when(roles.idsByKeys(List.of("editor"))).thenReturn(List.of(UUID.randomUUID()));
        doNothing().when(memberships).replaceRoles(eq(memId), any());
        when(groups.hasActiveMembership(uid, gid)).thenReturn(false);
        when(groups.insertGroupMembership(org, gid, uid, "active")).thenReturn(groupMemId);
        doNothing().when(groups).replaceGroupRoles(eq(groupMemId), any());
        doNothing().when(invites).markAccepted(any());
        when(memberships.findMembership(org, memId))
                .thenReturn(
                        Optional.of(
                                new MembershipRepository.MembershipRow(
                                        memId, uid, org, "active", "group_scoped", List.of("member"), Instant.now())));

        var m = svc.accept(uid, new InviteAcceptRequest(org, "tok"));

        assertThat(m.accessScope()).isEqualTo("group_scoped");
        verify(groups).insertGroupMembership(org, gid, uid, "active");
    }

    @Test
    void acceptOrgWideMemberSkipsReinsertForGroupInvite() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var gid = UUID.randomUUID();
        var memId = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(
                        Optional.of(
                                new InviteRepository.InviteAcceptData(
                                        UUID.randomUUID(), "a@b.co", gid, List.of("member"), List.of())));
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(
                        Optional.of(
                                new MembershipRepository.MembershipRow(
                                        memId, uid, org, "active", "org_wide", List.of("owner"), Instant.now())));
        when(groups.findById(org, gid))
                .thenReturn(
                        Optional.of(
                                new GroupRepository.GroupRow(
                                        gid, org, "G", "g", false, "active", Instant.now())));
        doNothing().when(invites).markAccepted(any());
        when(memberships.findMembership(org, memId))
                .thenReturn(
                        Optional.of(
                                new MembershipRepository.MembershipRow(
                                        memId, uid, org, "active", "org_wide", List.of("owner"), Instant.now())));

        svc.accept(uid, new InviteAcceptRequest(org, "tok"));

        verify(memberships, never()).insert(any(), any(), anyString(), anyString());
        verify(groups, never()).insertGroupMembership(any(), any(), any(), any());
        verify(invites).markAccepted(any());
    }

    @Test
    void acceptRejectsAlreadyInGroup() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var gid = UUID.randomUUID();
        var memId = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(
                        Optional.of(
                                new InviteRepository.InviteAcceptData(
                                        UUID.randomUUID(), "a@b.co", gid, List.of("member"), List.of())));
        when(memberships.findByUserAndOrg(uid, org))
                .thenReturn(
                        Optional.of(
                                new MembershipRepository.MembershipRow(
                                        memId, uid, org, "active", "group_scoped", List.of("member"), Instant.now())));
        when(groups.findById(org, gid))
                .thenReturn(
                        Optional.of(
                                new GroupRepository.GroupRow(
                                        gid, org, "G", "g", false, "active", Instant.now())));
        when(groups.hasActiveMembership(uid, gid)).thenReturn(true);

        assertThatThrownBy(() -> svc.accept(uid, new InviteAcceptRequest(org, "tok")))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void acceptRejectsInvalidGroup() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var gid = UUID.randomUUID();
        when(users.findById(uid))
                .thenReturn(Optional.of(new UserRepository.UserRow(uid, "a@b.co", null, "active", "A", "ru")));
        when(invites.findPendingByOrgAndTokenHash(eq(org), anyString()))
                .thenReturn(
                        Optional.of(
                                new InviteRepository.InviteAcceptData(
                                        UUID.randomUUID(), "a@b.co", gid, List.of("member"), List.of())));
        when(memberships.findByUserAndOrg(uid, org)).thenReturn(Optional.empty());
        when(groups.findById(org, gid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.accept(uid, new InviteAcceptRequest(org, "tok")))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void acceptSqlErrorWrapped() throws Exception {
        when(users.findById(any())).thenThrow(new SQLException("db"));
        assertThatThrownBy(() -> svc.accept(UUID.randomUUID(), new InviteAcceptRequest(UUID.randomUUID(), "t")))
                .isInstanceOf(IllegalStateException.class);
    }
}

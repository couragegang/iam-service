package com.couragegang.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPatchRequest;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpTestRequest;
import com.couragegang.iam.api.dto.OrgModels.InviteCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.MembershipPatchRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationPatchRequest;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.integration.ConfigWorkspaceClient;
import com.couragegang.iam.metrics.OutboundHttpMetrics;
import com.couragegang.iam.repo.GroupRepository;
import com.couragegang.iam.repo.IdpRepository;
import com.couragegang.iam.repo.InviteRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.OrganizationRepository;
import com.couragegang.iam.repo.RoleRepository;
import io.micronaut.http.HttpStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class OrganizationServiceTest {

    static final List<String> ALL_PERMS = List.of(
            "iam.org.read",
            "iam.org.update",
            "iam.member.read",
            "iam.member.manage",
            "iam.member.invite",
            "iam.idp.read",
            "iam.idp.manage",
            "iam.group.read",
            "iam.group.manage");

    @Mock
    OrganizationRepository orgRepo;

    @Mock
    GroupRepository groups;

    @Mock
    MembershipRepository memberships;

    @Mock
    RoleRepository roles;

    @Mock
    InviteRepository invites;

    @Mock
    IdpRepository idps;

    @Mock
    ConfigWorkspaceClient configWorkspaces;

    OrganizationService svc;
    UUID actor;
    UUID orgId;

    @BeforeEach
    void setUp() {
        var meterRegistry = new SimpleMeterRegistry();
        var outboundHttp = new OutboundHttpMetrics(meterRegistry);
        svc = new OrganizationService(
                orgRepo, groups, memberships, roles, invites, idps, outboundHttp, configWorkspaces);
        actor = UUID.randomUUID();
        orgId = UUID.randomUUID();
        lenient().when(roles.distinctPermissionKeysForRoleKeys(anyList())).thenReturn(ALL_PERMS);
    }

    private void activeMember() throws SQLException {
        when(memberships.findByUserAndOrg(actor, orgId))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), actor, orgId, "active", "org_wide", List.of("owner"), Instant.now())));
    }

    @Test
    void createSlugConflict() throws Exception {
        when(orgRepo.findIdBySlugLower("taken")).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> svc.create(actor, new OrganizationCreateRequest("N", "taken", null)))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.CONFLICT);
    }

    @Test
    void createSuccess() throws Exception {
        var newOrg = UUID.randomUUID();
        var defaultGroup = UUID.randomUUID();
        when(orgRepo.findIdBySlugLower("newco")).thenReturn(Optional.empty());
        when(orgRepo.insert(eq("Co"), eq("newco"), eq(null))).thenReturn(newOrg);
        when(groups.insertDefault(newOrg, "Co")).thenReturn(defaultGroup);
        when(memberships.insert(actor, newOrg, "active", "org_wide")).thenReturn(UUID.randomUUID());
        when(roles.idByKey("owner")).thenReturn(Optional.of(UUID.randomUUID()));
        when(orgRepo.findById(newOrg))
                .thenReturn(Optional.of(new OrganizationRepository.OrgRow(newOrg, "Co", "newco", null, Instant.now())));
        var org = svc.create(actor, new OrganizationCreateRequest("Co", "newco", null));
        assertThat(org.slug()).isEqualTo("newco");
        assertThat(org.defaultGroupId()).isEqualTo(defaultGroup);
    }

    @Test
    void createOwnerRoleMissing() throws Exception {
        when(orgRepo.findIdBySlugLower("x")).thenReturn(Optional.empty());
        when(orgRepo.insert(anyString(), eq("x"), any())).thenReturn(orgId);
        when(groups.insertDefault(orgId, "N")).thenReturn(UUID.randomUUID());
        when(memberships.insert(actor, orgId, "active", "org_wide")).thenReturn(UUID.randomUUID());
        when(roles.idByKey("owner")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.create(actor, new OrganizationCreateRequest("N", "x", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getNotMember() throws Exception {
        when(memberships.findByUserAndOrg(actor, orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.get(actor, orgId)).isInstanceOf(IamApiException.class);
    }

    @Test
    void getInactiveMember() throws Exception {
        when(memberships.findByUserAndOrg(actor, orgId))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), actor, orgId, "suspended", "org_wide", List.of("owner"), Instant.now())));
        assertThatThrownBy(() -> svc.get(actor, orgId)).isInstanceOf(IamApiException.class);
    }

    @Test
    void getOrgMissing() throws Exception {
        activeMember();
        when(orgRepo.findById(orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.get(actor, orgId)).isInstanceOf(IamApiException.class);
    }

    @Test
    void getSuccess() throws Exception {
        activeMember();
        var defaultGroup = UUID.randomUUID();
        when(orgRepo.findById(orgId))
                .thenReturn(Optional.of(new OrganizationRepository.OrgRow(orgId, "O", "o", null, Instant.now())));
        when(groups.findDefaultByOrg(orgId))
                .thenReturn(Optional.of(new GroupRepository.GroupRow(
                        defaultGroup, orgId, "O", "default", true, "active", Instant.now())));
        assertThat(svc.get(actor, orgId).name()).isEqualTo("O");
    }

    @Test
    void patchSuccess() throws Exception {
        activeMember();
        var defaultGroup = UUID.randomUUID();
        doNothing().when(orgRepo).update(eq(orgId), any(), any());
        when(orgRepo.findById(orgId))
                .thenReturn(Optional.of(new OrganizationRepository.OrgRow(orgId, "N", "s", null, Instant.now())));
        when(groups.findDefaultByOrg(orgId))
                .thenReturn(Optional.of(new GroupRepository.GroupRow(
                        defaultGroup, orgId, "N", "default", true, "active", Instant.now())));
        var o = svc.patch(actor, orgId, new OrganizationPatchRequest("N", "s"));
        assertThat(o.name()).isEqualTo("N");
    }

    @Test
    void membersList() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        when(memberships.listByOrg(orgId, 10))
                .thenReturn(List.of(new MembershipRepository.MembershipRow(
                        mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("member"), Instant.now())));
        var page = svc.members(actor, orgId, 10);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void patchMemberStatusOnly() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        var invited = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "invited", "group_scoped", List.of("member"), null);
        var active = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("member"), Instant.now());
        when(memberships.findMembership(orgId, mid)).thenReturn(Optional.of(invited), Optional.of(active));
        var m = svc.patchMember(actor, orgId, mid, new MembershipPatchRequest("active", null));
        assertThat(m.status()).isEqualTo("active");
    }

    @Test
    void patchMemberRoleKeysEmptySkipsReplace() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        var row = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("member"), Instant.now());
        when(memberships.findMembership(orgId, mid)).thenReturn(Optional.of(row), Optional.of(row));
        svc.patchMember(actor, orgId, mid, new MembershipPatchRequest(null, List.of()));
        verify(memberships, never()).replaceRoles(any(), any());
    }

    @Test
    void patchMemberLastOwnerRemovalThrows() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        var ownerOnly = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("owner"), Instant.now());
        when(memberships.findMembership(orgId, mid)).thenReturn(Optional.of(ownerOnly), Optional.of(ownerOnly));
        when(roles.idsByKeys(List.of("member"))).thenReturn(List.of(UUID.randomUUID()));
        when(memberships.countOwnersInOrg(orgId)).thenReturn(1L);
        assertThatThrownBy(() ->
                        svc.patchMember(actor, orgId, mid, new MembershipPatchRequest(null, List.of("member"))))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void patchMemberUnknownRole() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        var mixed = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("owner", "member"), Instant.now());
        when(memberships.findMembership(orgId, mid)).thenReturn(Optional.of(mixed), Optional.of(mixed));
        when(roles.idsByKeys(List.of("bad"))).thenReturn(List.of());
        when(memberships.countOwnersInOrg(orgId)).thenReturn(2L);
        assertThatThrownBy(() -> svc.patchMember(actor, orgId, mid, new MembershipPatchRequest(null, List.of("bad"))))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void patchMemberRolesOk() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        var rid = UUID.randomUUID();
        var memberRow = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("member"), Instant.now());
        var ownerRow = new MembershipRepository.MembershipRow(
                mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("owner"), Instant.now());
        when(memberships.findMembership(orgId, mid)).thenReturn(Optional.of(memberRow), Optional.of(ownerRow));
        when(roles.idsByKeys(List.of("owner"))).thenReturn(List.of(rid));
        doNothing().when(memberships).replaceRoles(mid, List.of(rid));
        var m = svc.patchMember(actor, orgId, mid, new MembershipPatchRequest(null, List.of("owner")));
        assertThat(m.roles()).contains("owner");
    }

    @Test
    void deleteLastOwnerThrows() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        when(memberships.findMembership(orgId, mid))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("owner"), Instant.now())));
        when(memberships.countOwnersInOrg(orgId)).thenReturn(1L);
        assertThatThrownBy(() -> svc.deleteMember(actor, orgId, mid)).isInstanceOf(IamApiException.class);
    }

    @Test
    void deleteMemberOk() throws Exception {
        activeMember();
        var mid = UUID.randomUUID();
        when(memberships.findMembership(orgId, mid))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        mid, UUID.randomUUID(), orgId, "active", "org_wide", List.of("member"), Instant.now())));
        doNothing().when(memberships).deleteMembership(mid);
        svc.deleteMember(actor, orgId, mid);
        verify(memberships).deleteMembership(mid);
    }

    @Test
    void listInvites() throws Exception {
        activeMember();
        var iid = UUID.randomUUID();
        when(invites.listPending(orgId))
                .thenReturn(List.of(new InviteRepository.InviteRow(
                        iid, "e@x.co", null, Instant.now(), Instant.now(), null, List.of("member"), List.of())));
        var list = svc.listInvites(actor, orgId);
        assertThat(list.items()).hasSize(1);
    }

    @Test
    void createInviteUnknownRole() throws Exception {
        activeMember();
        when(roles.idsByKeys(List.of("x"))).thenReturn(List.of());
        assertThatThrownBy(() ->
                        svc.createInvite(actor, orgId, new InviteCreateRequest("a@b.co", List.of("x"), null, null, null)))
                .isInstanceOf(IamApiException.class);
    }

    @Test
    void createInviteSuccess() throws Exception {
        activeMember();
        var iid = UUID.randomUUID();
        when(invites.insertInvite(eq(orgId), eq(null), eq("a@b.co"), anyString(), any(), any()))
                .thenReturn(iid);
        when(roles.idsByKeys(List.of("member"))).thenReturn(List.of(UUID.randomUUID()));
        doNothing().when(invites).attachRoles(eq(iid), anyList());
        when(invites.listPending(orgId))
                .thenReturn(List.of(new InviteRepository.InviteRow(
                        iid, "a@b.co", null, Instant.now(), Instant.now(), null, List.of("member"), List.of())));
        var created = svc.createInvite(actor, orgId, new InviteCreateRequest("A@b.co", List.of("member"), null, null, 24));
        assertThat(created.email()).isEqualTo("a@b.co");
    }

    @Test
    void revokeInvite() throws Exception {
        activeMember();
        var iid = UUID.randomUUID();
        when(invites.revokeForOrg(orgId, iid)).thenReturn(true);
        svc.revokeInvite(actor, orgId, iid);
        verify(invites).revokeForOrg(orgId, iid);
    }

    @Test
    void revokeInviteWrongOrgOrUnknownId() throws Exception {
        activeMember();
        var iid = UUID.randomUUID();
        when(invites.revokeForOrg(orgId, iid)).thenReturn(false);
        assertThatThrownBy(() -> svc.revokeInvite(actor, orgId, iid))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.NOT_FOUND);
        verify(invites).revokeForOrg(orgId, iid);
    }

    @Test
    void idpGetMissing() throws Exception {
        activeMember();
        when(idps.findByOrg(orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.idpGet(actor, orgId)).isInstanceOf(IamApiException.class);
    }

    @Test
    void idpGetSuccess() throws Exception {
        activeMember();
        var row = new IdpRepository.IdpRow(
                UUID.randomUUID(), "oidc", "https://idp", "cid", "https://md", true, false, null);
        when(idps.findByOrg(orgId)).thenReturn(Optional.of(row));
        var pub = svc.idpGet(actor, orgId);
        assertThat(pub.clientId()).isEqualTo("cid");
    }

    @Test
    void idpPatch() throws Exception {
        activeMember();
        var rid = UUID.randomUUID();
        when(roles.idByKey("member")).thenReturn(Optional.of(rid));
        doNothing().when(idps).upsert(any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
        var row = new IdpRepository.IdpRow(UUID.randomUUID(), "oidc", "i", "c", "m", true, true, rid);
        when(idps.findByOrg(orgId)).thenReturn(Optional.of(row));
        var pub = svc.idpPatch(
                actor,
                orgId,
                new OrgIdpConfigPatchRequest(
                        null, "https://iss", "id", "secret", "https://md", true, true, "member"));
        assertThat(pub.issuer()).isEqualTo("https://iss");
    }

    @Test
    void idpPatchDefaultRoleUnknown() throws Exception {
        activeMember();
        when(roles.idByKey("ghost")).thenReturn(Optional.empty());
        doNothing().when(idps).upsert(any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), eq(null));
        var row = new IdpRepository.IdpRow(UUID.randomUUID(), "oidc", null, null, null, false, false, null);
        when(idps.findByOrg(orgId)).thenReturn(Optional.of(row));
        svc.idpPatch(actor, orgId, new OrgIdpConfigPatchRequest(null, null, null, null, null, null, null, "ghost"));
        verify(idps).upsert(any(), anyString(), any(), any(), any(), any(), anyBoolean(), anyBoolean(), eq(null));
    }

    @Test
    void idpTestBlankUrl() throws Exception {
        activeMember();
        var r = svc.idpTest(actor, orgId, new OrgIdpTestRequest("  "));
        assertThat(r.ok()).isFalse();
        var r2 = svc.idpTest(actor, orgId, null);
        assertThat(r2.ok()).isFalse();
    }

    @Test
    void idpTestHttpOk() throws Exception {
        activeMember();
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            server.start();
            var url = server.url("/.well-known").toString();
            var r = svc.idpTest(actor, orgId, new OrgIdpTestRequest(url));
            assertThat(r.ok()).isTrue();
        }
    }

    @Test
    void idpTestHttpError() throws Exception {
        activeMember();
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500));
            server.start();
            var r = svc.idpTest(actor, orgId, new OrgIdpTestRequest(server.url("/").toString()));
            assertThat(r.ok()).isFalse();
        }
    }

    @Test
    void missingPermission() throws Exception {
        when(memberships.findByUserAndOrg(actor, orgId))
                .thenReturn(Optional.of(new MembershipRepository.MembershipRow(
                        UUID.randomUUID(), actor, orgId, "active", "org_wide", List.of("member"), Instant.now())));
        when(roles.distinctPermissionKeysForRoleKeys(List.of("member"))).thenReturn(List.of("iam.member.read"));
        assertThatThrownBy(() -> svc.get(actor, orgId)).isInstanceOf(IamApiException.class);
    }
}

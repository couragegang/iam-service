package com.couragegang.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPatchRequest;
import com.couragegang.iam.api.dto.OrgModels.InviteCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.MembershipPatchRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationPatchRequest;
import com.couragegang.iam.service.OrganizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class OrganizationsControllerTest {

    @Mock
    OrganizationService orgs;

    @Test
    void delegatesCrud() {
        var c = new OrganizationsController(orgs);
        var uid = UUID.randomUUID().toString();
        var oid = UUID.randomUUID();
        var mid = UUID.randomUUID();
        var iid = UUID.randomUUID();
        c.orgCreate(uid, new OrganizationCreateRequest("N", "slug", null));
        verify(orgs).create(UUID.fromString(uid), any(OrganizationCreateRequest.class));
        c.orgGet(uid, oid);
        verify(orgs).get(UUID.fromString(uid), oid);
        c.orgPatch(uid, oid, new OrganizationPatchRequest("x", null));
        verify(orgs).patch(UUID.fromString(uid), oid, any(OrganizationPatchRequest.class));
        c.orgMembersList(uid, oid, null, 20);
        verify(orgs).members(UUID.fromString(uid), oid, 20);
        c.orgMemberPatch(uid, oid, mid, new MembershipPatchRequest("active", null));
        verify(orgs).patchMember(UUID.fromString(uid), oid, mid, any(MembershipPatchRequest.class));
        c.orgMemberDelete(uid, oid, mid);
        verify(orgs).deleteMember(UUID.fromString(uid), oid, mid);
        c.orgInvitesList(uid, oid);
        verify(orgs).listInvites(UUID.fromString(uid), oid);
        c.orgInvitesCreate(uid, oid, new InviteCreateRequest("a@b.co", List.of("member"), null));
        verify(orgs).createInvite(UUID.fromString(uid), oid, any(InviteCreateRequest.class));
        c.orgInvitesRevoke(uid, oid, iid);
        verify(orgs).revokeInvite(UUID.fromString(uid), oid, iid);
        c.orgIdpGet(uid, oid);
        verify(orgs).idpGet(UUID.fromString(uid), oid);
        c.orgIdpPatch(uid, oid, new OrgIdpConfigPatchRequest(null, null, null, null, null, null, null, null));
        verify(orgs).idpPatch(UUID.fromString(uid), oid, any(OrgIdpConfigPatchRequest.class));
        c.orgIdpTest(uid, oid, null);
        verify(orgs).idpTest(UUID.fromString(uid), oid, null);
    }
}

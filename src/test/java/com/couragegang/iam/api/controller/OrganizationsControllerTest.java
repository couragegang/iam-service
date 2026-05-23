package com.couragegang.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        var actor = UUID.fromString(uid);
        verify(orgs).create(eq(actor), any(OrganizationCreateRequest.class));
        c.orgGet(uid, oid);
        verify(orgs).get(eq(actor), eq(oid));
        c.orgPatch(uid, oid, new OrganizationPatchRequest("x", null));
        verify(orgs).patch(eq(actor), eq(oid), any(OrganizationPatchRequest.class));
        c.orgMembersList(uid, oid, null, 20);
        verify(orgs).members(eq(actor), eq(oid), eq(20));
        c.orgMemberPatch(uid, oid, mid, new MembershipPatchRequest("active", null));
        verify(orgs).patchMember(eq(actor), eq(oid), eq(mid), any(MembershipPatchRequest.class));
        c.orgMemberDelete(uid, oid, mid);
        verify(orgs).deleteMember(eq(actor), eq(oid), eq(mid));
        c.orgInvitesList(uid, oid);
        verify(orgs).listInvites(eq(actor), eq(oid));
        c.orgInvitesCreate(uid, oid, new InviteCreateRequest("a@b.co", List.of("member"), null, null, null));
        verify(orgs).createInvite(eq(actor), eq(oid), any(InviteCreateRequest.class));
        c.orgInvitesRevoke(uid, oid, iid);
        verify(orgs).revokeInvite(eq(actor), eq(oid), eq(iid));
        c.orgIdpGet(uid, oid);
        verify(orgs).idpGet(eq(actor), eq(oid));
        c.orgIdpPatch(uid, oid, new OrgIdpConfigPatchRequest(null, null, null, null, null, null, null, null));
        verify(orgs).idpPatch(eq(actor), eq(oid), any(OrgIdpConfigPatchRequest.class));
        c.orgIdpTest(uid, oid, null);
        verify(orgs).idpTest(eq(actor), eq(oid), eq(null));
    }
}

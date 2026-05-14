package com.couragegang.iam.api.controller;

import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPatchRequest;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPublic;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpTestRequest;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpTestResult;
import com.couragegang.iam.api.dto.OrgModels.InviteCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.InviteCreated;
import com.couragegang.iam.api.dto.OrgModels.InviteListResponse;
import com.couragegang.iam.api.dto.OrgModels.MemberPage;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.api.dto.OrgModels.MembershipPatchRequest;
import com.couragegang.iam.api.dto.OrgModels.Organization;
import com.couragegang.iam.api.dto.OrgModels.OrganizationCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationPatchRequest;
import com.couragegang.iam.security.SecurityAttributes;
import com.couragegang.iam.service.OrganizationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestAttribute;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/organizations")
public final class OrganizationsController {

    private final OrganizationService orgs;

    public OrganizationsController(OrganizationService orgs) {
        this.orgs = orgs;
    }

    @Post
    public HttpResponse<Organization> orgCreate(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @Body @Valid OrganizationCreateRequest body) {
        return HttpResponse.created(orgs.create(UUID.fromString(userId), body));
    }

    @Get("/{orgId}")
    public HttpResponse<Organization> orgGet(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @PathVariable UUID orgId) {
        return HttpResponse.ok(orgs.get(UUID.fromString(userId), orgId));
    }

    @Patch("/{orgId}")
    public HttpResponse<Organization> orgPatch(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @Body @Valid OrganizationPatchRequest body) {
        return HttpResponse.ok(orgs.patch(UUID.fromString(userId), orgId, body));
    }

    @Get("/{orgId}/members")
    public HttpResponse<MemberPage> orgMembersList(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @Nullable @QueryValue String cursor,
            @QueryValue(defaultValue = "20") int limit) {
        return HttpResponse.ok(orgs.members(UUID.fromString(userId), orgId, limit));
    }

    @Patch("/{orgId}/members/{membershipId}")
    public HttpResponse<Membership> orgMemberPatch(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @PathVariable UUID membershipId,
            @Body @Valid MembershipPatchRequest body) {
        return HttpResponse.ok(orgs.patchMember(UUID.fromString(userId), orgId, membershipId, body));
    }

    @Delete("/{orgId}/members/{membershipId}")
    public HttpResponse<Void> orgMemberDelete(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @PathVariable UUID membershipId) {
        orgs.deleteMember(UUID.fromString(userId), orgId, membershipId);
        return HttpResponse.noContent();
    }

    @Get("/{orgId}/invites")
    public HttpResponse<InviteListResponse> orgInvitesList(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @PathVariable UUID orgId) {
        return HttpResponse.ok(orgs.listInvites(UUID.fromString(userId), orgId));
    }

    @Post("/{orgId}/invites")
    public HttpResponse<InviteCreated> orgInvitesCreate(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @Body @Valid InviteCreateRequest body) {
        return HttpResponse.created(orgs.createInvite(UUID.fromString(userId), orgId, body));
    }

    @Delete("/{orgId}/invites/{inviteId}")
    public HttpResponse<Void> orgInvitesRevoke(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @PathVariable UUID inviteId) {
        orgs.revokeInvite(UUID.fromString(userId), orgId, inviteId);
        return HttpResponse.noContent();
    }

    @Get("/{orgId}/idp")
    public HttpResponse<OrgIdpConfigPublic> orgIdpGet(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @PathVariable UUID orgId) {
        return HttpResponse.ok(orgs.idpGet(UUID.fromString(userId), orgId));
    }

    @Patch("/{orgId}/idp")
    public HttpResponse<OrgIdpConfigPublic> orgIdpPatch(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @Body @Valid OrgIdpConfigPatchRequest body) {
        return HttpResponse.ok(orgs.idpPatch(UUID.fromString(userId), orgId, body));
    }

    @Post("/{orgId}/idp/test")
    public HttpResponse<OrgIdpTestResult> orgIdpTest(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId,
            @PathVariable UUID orgId,
            @Body @Nullable OrgIdpTestRequest body) {
        return HttpResponse.ok(orgs.idpTest(UUID.fromString(userId), orgId, body));
    }
}

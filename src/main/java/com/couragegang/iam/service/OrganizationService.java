package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPatchRequest;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpConfigPublic;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpTestRequest;
import com.couragegang.iam.api.dto.IdpModels.OrgIdpTestResult;
import com.couragegang.iam.api.dto.OrgModels.Invite;
import com.couragegang.iam.api.dto.OrgModels.InviteCreated;
import com.couragegang.iam.api.dto.OrgModels.InviteCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.InviteListResponse;
import com.couragegang.iam.api.dto.OrgModels.MemberPage;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.api.dto.OrgModels.MembershipPatchRequest;
import com.couragegang.iam.api.dto.OrgModels.Organization;
import com.couragegang.iam.api.dto.OrgModels.OrganizationCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationGroup;
import com.couragegang.iam.api.dto.OrgModels.OrganizationGroupCreateRequest;
import com.couragegang.iam.api.dto.OrgModels.OrganizationGroupListResponse;
import com.couragegang.iam.api.dto.OrgModels.OrganizationPatchRequest;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.integration.ConfigWorkspaceClient;
import com.couragegang.iam.metrics.OutboundHttpMetrics;
import com.couragegang.iam.repo.IdpRepository;
import com.couragegang.iam.repo.GroupRepository;
import com.couragegang.iam.repo.InviteRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.OrganizationRepository;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.security.HexSha256;
import io.micronaut.http.HttpStatus;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Singleton
public final class OrganizationService {

    private static final SecureRandom RND = new SecureRandom();

    private final OrganizationRepository orgs;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final RoleRepository roles;
    private final InviteRepository invites;
    private final IdpRepository idps;
    private final OutboundHttpMetrics outboundHttp;
    private final ConfigWorkspaceClient configWorkspaces;

    public OrganizationService(
            OrganizationRepository orgs,
            GroupRepository groups,
            MembershipRepository memberships,
            RoleRepository roles,
            InviteRepository invites,
            IdpRepository idps,
            OutboundHttpMetrics outboundHttp,
            ConfigWorkspaceClient configWorkspaces) {
        this.orgs = orgs;
        this.groups = groups;
        this.memberships = memberships;
        this.roles = roles;
        this.invites = invites;
        this.idps = idps;
        this.outboundHttp = outboundHttp;
        this.configWorkspaces = configWorkspaces;
    }

    public Organization create(UUID actorId, OrganizationCreateRequest req) {
        try {
            var slug = req.slug().trim().toLowerCase();
            if (orgs.findIdBySlugLower(slug).isPresent()) {
                throw new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "slug taken");
            }
            var orgId = orgs.insert(req.name().trim(), slug, req.planTier());
            var defaultGroupId = groups.insertDefault(orgId, req.name().trim());
            var memId = memberships.insert(actorId, orgId, "active", "org_wide");
            var ownerId = roles.idByKey("owner").orElseThrow();
            memberships.addRole(memId, ownerId);
            configWorkspaces.bootstrapDefaultWorkspace(orgId, defaultGroupId, req.name().trim());
            return toOrg(orgs.findById(orgId).orElseThrow(), defaultGroupId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Organization get(UUID actorId, UUID orgId) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.org.read");
            var row = orgs.findById(orgId).orElseThrow(() -> notFound());
            var defaultGroupId = groups.findDefaultByOrg(orgId).map(GroupRepository.GroupRow::id).orElseThrow();
            return toOrg(row, defaultGroupId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Organization patch(UUID actorId, UUID orgId, OrganizationPatchRequest req) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.org.update");
            orgs.update(orgId, req.name(), req.slug());
            var row = orgs.findById(orgId).orElseThrow(() -> notFound());
            var defaultGroupId = groups.findDefaultByOrg(orgId).map(GroupRepository.GroupRow::id).orElseThrow();
            return toOrg(row, defaultGroupId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public MemberPage members(UUID actorId, UUID orgId, int limit) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.member.read");
            var rows = memberships.listByOrg(orgId, limit);
            var items = rows.stream()
                    .map(r -> new Membership(
                            r.id(), r.userId(), r.orgId(), r.status(), r.accessScope(), r.roleKeys(), r.joinedAt()))
                    .toList();
            return new MemberPage(items, null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Membership patchMember(UUID actorId, UUID orgId, UUID membershipId, MembershipPatchRequest req) {
        try {
            var actorMem = requireActiveMember(actorId, orgId);
            requirePerm(actorMem, "iam.member.manage");
            var target = memberships.findMembership(orgId, membershipId).orElseThrow(() -> notFound());
            if (req.status() != null) {
                memberships.updateStatus(membershipId, req.status());
            }
            if (req.roleKeys() != null && !req.roleKeys().isEmpty()) {
                if (!req.roleKeys().contains("owner")) {
                    var cur = memberships.findMembership(orgId, membershipId).orElseThrow();
                    if (cur.roleKeys().contains("owner") && memberships.countOwnersInOrg(orgId) <= 1) {
                        throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "last owner");
                    }
                }
                var roleIds = roles.idsByKeys(req.roleKeys());
                if (roleIds.size() != req.roleKeys().size()) {
                    throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown role key");
                }
                memberships.replaceRoles(membershipId, roleIds);
            }
            return memberships
                    .findMembership(orgId, membershipId)
                    .map(r -> new Membership(
                            r.id(), r.userId(), r.orgId(), r.status(), r.accessScope(), r.roleKeys(), r.joinedAt()))
                    .orElseThrow();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteMember(UUID actorId, UUID orgId, UUID membershipId) {
        try {
            var actorMem = requireActiveMember(actorId, orgId);
            requirePerm(actorMem, "iam.member.manage");
            var target = memberships.findMembership(orgId, membershipId).orElseThrow(() -> notFound());
            if (target.roleKeys().contains("owner") && memberships.countOwnersInOrg(orgId) <= 1) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "last owner");
            }
            memberships.deleteMembership(membershipId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public InviteListResponse listInvites(UUID actorId, UUID orgId) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.member.read");
            var rows = invites.listPending(orgId);
            var items = rows.stream()
                    .map(r -> new Invite(
                            r.id(),
                            r.email(),
                            r.groupId(),
                            r.roleKeys(),
                            r.groupRoleKeys(),
                            r.expiresAt(),
                            r.createdAt(),
                            r.acceptedAt()))
                    .toList();
            return new InviteListResponse(items);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public InviteCreated createInvite(UUID actorId, UUID orgId, InviteCreateRequest req) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.member.invite");
            var ttl = req.ttlHours() == null ? 168 : req.ttlHours();
            var raw = randomToken();
            var hash = HexSha256.hashUtf8(raw);
            var exp = Instant.now().plus(ttl, ChronoUnit.HOURS);
            if (req.groupId() != null && groups.findById(orgId, req.groupId()).isEmpty()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown group");
            }
            var inviteId = invites.insertInvite(
                    orgId, req.groupId(), req.email().trim().toLowerCase(), hash, exp, m.id());
            var roleIds = roles.idsByKeys(req.roleKeys());
            if (roleIds.size() != req.roleKeys().size()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown role key");
            }
            invites.attachRoles(inviteId, roleIds);
            if (req.groupId() != null && req.groupRoleKeys() != null && !req.groupRoleKeys().isEmpty()) {
                var groupRoleIds = roles.idsByKeys(req.groupRoleKeys());
                if (groupRoleIds.size() != req.groupRoleKeys().size()) {
                    throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown group role key");
                }
                invites.attachGroupRoles(inviteId, groupRoleIds);
            }
            var row = invites.listPending(orgId).stream().filter(x -> x.id().equals(inviteId)).findFirst().orElseThrow();
            return new InviteCreated(
                    row.id(),
                    row.email(),
                    row.groupId(),
                    row.roleKeys(),
                    row.groupRoleKeys(),
                    row.expiresAt(),
                    row.createdAt(),
                    row.acceptedAt(),
                    null);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void revokeInvite(UUID actorId, UUID orgId, UUID inviteId) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.member.manage");
            if (!invites.revokeForOrg(orgId, inviteId)) {
                throw notFound();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrganizationGroupListResponse listGroups(UUID actorId, UUID orgId, int limit) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.group.read");
            var rows = groups.listByOrg(orgId, limit);
            var items = rows.stream().map(OrganizationService::toGroup).toList();
            return new OrganizationGroupListResponse(items);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrganizationGroup createGroup(UUID actorId, UUID orgId, OrganizationGroupCreateRequest req) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.group.manage");
            var slug = req.slug().trim().toLowerCase();
            if ("default".equals(slug)) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reserved slug");
            }
            var groupId = groups.insert(orgId, req.name().trim(), slug, false);
            return toGroup(groups.findById(orgId, groupId).orElseThrow());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrgIdpConfigPublic idpGet(UUID actorId, UUID orgId) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.idp.read");
            var row = idps.findByOrg(orgId).orElseThrow(() -> notFound());
            return mapIdpPublic(row);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrgIdpConfigPublic idpPatch(UUID actorId, UUID orgId, OrgIdpConfigPatchRequest req) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.idp.manage");
            UUID defaultRoleId = null;
            if (req.defaultRoleKey() != null) {
                defaultRoleId = roles.idByKey(req.defaultRoleKey()).orElse(null);
            }
            String secretEnc = null;
            if (req.clientSecret() != null && !req.clientSecret().isBlank()) {
                secretEnc = Base64.getEncoder().encodeToString(req.clientSecret().getBytes(StandardCharsets.UTF_8));
            }
            idps.upsert(
                    orgId,
                    req.type() != null ? req.type() : "oidc",
                    req.issuer(),
                    req.clientId(),
                    secretEnc,
                    req.metadataUrl(),
                    req.enabled() != null && req.enabled(),
                    req.jitProvisioning() != null && req.jitProvisioning(),
                    defaultRoleId);
            return idpGet(actorId, orgId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrgIdpTestResult idpTest(UUID actorId, UUID orgId, @Nullable OrgIdpTestRequest body) {
        try {
            var m = requireActiveMember(actorId, orgId);
            requirePerm(m, "iam.idp.manage");
            var url = body != null ? body.metadataUrl() : null;
            if (url == null || url.isBlank()) {
                return new OrgIdpTestResult(false, "metadataUrl required", null);
            }
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            var request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            var resp = outboundHttp.send(client, request, "idp", "metadata_probe");
            return new OrgIdpTestResult(resp.statusCode() < 400, "http " + resp.statusCode(), url);
        } catch (Exception e) {
            return new OrgIdpTestResult(false, e.getMessage(), null);
        }
    }

    private static String randomToken() {
        var b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private MembershipRepository.MembershipRow requireActiveMember(UUID userId, UUID orgId) throws SQLException {
        return memberships
                .findByUserAndOrg(userId, orgId)
                .filter(m -> "active".equals(m.status()))
                .orElseThrow(() -> new IamApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "not a member"));
    }

    private void requirePerm(MembershipRepository.MembershipRow m, String perm) throws SQLException {
        var keys = roles.distinctPermissionKeysForRoleKeys(m.roleKeys());
        if (!keys.contains(perm)) {
            throw new IamApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "missing " + perm);
        }
    }

    private static IamApiException notFound() {
        return new IamApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not found");
    }

    private static Organization toOrg(OrganizationRepository.OrgRow r, UUID defaultGroupId) {
        return new Organization(r.id(), r.name(), r.slug(), r.planTier(), defaultGroupId, r.createdAt());
    }

    private static OrganizationGroup toGroup(GroupRepository.GroupRow r) {
        return new OrganizationGroup(r.id(), r.orgId(), r.name(), r.slug(), r.isDefault(), r.status(), r.createdAt());
    }

    private static OrgIdpConfigPublic mapIdpPublic(IdpRepository.IdpRow r) {
        return new OrgIdpConfigPublic(
                r.id(),
                r.type(),
                r.issuer(),
                r.clientId(),
                r.metadataUrl(),
                r.enabled(),
                r.jitProvisioning(),
                null);
    }
}

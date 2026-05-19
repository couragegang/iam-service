package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.OrgModels.InviteAcceptRequest;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.repo.GroupRepository;
import com.couragegang.iam.repo.InviteRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.HexSha256;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Singleton
public final class InviteService {

    private final InviteRepository invites;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final GroupRepository groups;
    private final RoleRepository roles;

    public InviteService(
            InviteRepository invites,
            UserRepository users,
            MembershipRepository memberships,
            GroupRepository groups,
            RoleRepository roles) {
        this.invites = invites;
        this.users = users;
        this.memberships = memberships;
        this.groups = groups;
        this.roles = roles;
    }

    public Membership accept(UUID userId, InviteAcceptRequest req) {
        try {
            var user = users.findById(userId).orElseThrow();
            var email = user.email().toLowerCase(Locale.ROOT);
            var hash = HexSha256.hashUtf8(req.token());
            var data = invites
                    .findPendingByOrgAndTokenHash(req.orgId(), hash)
                    .orElseThrow(() -> new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid invite"));
            if (!data.email().equalsIgnoreCase(email)) {
                throw new IamApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "email mismatch");
            }
            var existing = memberships.findByUserAndOrg(userId, req.orgId());
            if (data.groupId() == null) {
                if (existing.isPresent()) {
                    throw new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "already a member");
                }
                var memId = memberships.insert(userId, req.orgId(), "active", "org_wide");
                applyOrgRoles(memId, data.roleKeys());
                invites.markAccepted(data.inviteId());
                return toMembership(req.orgId(), memId);
            }
            if (data.groupId() != null && groups.findById(req.orgId(), data.groupId()).isEmpty()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid invite group");
            }
            UUID memId;
            if (existing.isEmpty()) {
                memId = memberships.insert(userId, req.orgId(), "active", "group_scoped");
                applyOrgRoles(memId, data.roleKeys());
            } else {
                var row = existing.get();
                if ("org_wide".equals(row.accessScope())) {
                    invites.markAccepted(data.inviteId());
                    return toMembership(req.orgId(), row.id());
                }
                memId = row.id();
            }
            if (groups.hasActiveMembership(userId, data.groupId())) {
                throw new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "already in group");
            }
            var groupMemId = groups.insertGroupMembership(req.orgId(), data.groupId(), userId, "active");
            applyGroupRoles(groupMemId, data.groupRoleKeys());
            invites.markAccepted(data.inviteId());
            return toMembership(req.orgId(), memId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void applyOrgRoles(UUID membershipId, List<String> roleKeys) throws SQLException {
        var roleIds = roles.idsByKeys(roleKeys);
        if (roleIds.size() != roleKeys.size()) {
            throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown role key");
        }
        memberships.replaceRoles(membershipId, roleIds);
    }

    private void applyGroupRoles(UUID groupMembershipId, List<String> groupRoleKeys) throws SQLException {
        var keys = groupRoleKeys.isEmpty() ? List.of("member") : groupRoleKeys;
        var roleIds = roles.idsByKeys(keys);
        if (roleIds.size() != keys.size()) {
            throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unknown group role key");
        }
        groups.replaceGroupRoles(groupMembershipId, roleIds);
    }

    private Membership toMembership(UUID orgId, UUID membershipId) throws SQLException {
        return memberships
                .findMembership(orgId, membershipId)
                .map(r -> new Membership(
                        r.id(), r.userId(), r.orgId(), r.status(), r.accessScope(), r.roleKeys(), r.joinedAt()))
                .orElseThrow();
    }
}

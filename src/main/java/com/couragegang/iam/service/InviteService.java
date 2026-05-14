package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.OrgModels.InviteAcceptRequest;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.repo.InviteRepository;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.RoleRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.HexSha256;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

@Singleton
public final class InviteService {

    private final InviteRepository invites;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final RoleRepository roles;

    public InviteService(
            InviteRepository invites,
            UserRepository users,
            MembershipRepository memberships,
            RoleRepository roles) {
        this.invites = invites;
        this.users = users;
        this.memberships = memberships;
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
            if (memberships.findByUserAndOrg(userId, req.orgId()).isPresent()) {
                throw new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "already a member");
            }
            var memId = memberships.insert(userId, req.orgId(), "active");
            var roleIds = roles.idsByKeys(data.roleKeys());
            memberships.replaceRoles(memId, roleIds);
            invites.markAccepted(data.inviteId());
            return memberships
                    .findMembership(req.orgId(), memId)
                    .map(r -> new Membership(r.id(), r.userId(), r.orgId(), r.status(), r.roleKeys(), r.joinedAt()))
                    .orElseThrow();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}

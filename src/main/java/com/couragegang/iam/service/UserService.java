package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.UserModels.ChangePasswordRequest;
import com.couragegang.iam.api.dto.UserModels.MeOrgSummary;
import com.couragegang.iam.api.dto.UserModels.MePatchRequest;
import com.couragegang.iam.api.dto.UserModels.MeResponse;
import com.couragegang.iam.api.dto.UserModels.RefreshSessionListResponse;
import com.couragegang.iam.api.dto.UserModels.RefreshSessionPublic;
import com.couragegang.iam.api.dto.UserModels.UserPublic;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.repo.MembershipRepository;
import com.couragegang.iam.repo.RefreshSessionRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.PasswordHasher;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Singleton
public final class UserService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PasswordHasher passwords;
    private final RefreshSessionRepository sessions;

    public UserService(
            UserRepository users,
            MembershipRepository memberships,
            PasswordHasher passwords,
            RefreshSessionRepository sessions) {
        this.users = users;
        this.memberships = memberships;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public MeResponse me(UUID userId) {
        try {
            var u = users.findById(userId).orElseThrow(() -> new IamApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "user"));
            var orgs = memberships.listOrgsForUser(userId).stream()
                    .map(r -> new MeOrgSummary(r.orgId(), r.slug(), r.name(), r.roles()))
                    .toList();
            return new MeResponse(toPublic(u), orgs);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public UserPublic patch(UUID userId, MePatchRequest req) {
        try {
            users.updateProfile(userId, req.displayName(), req.locale());
            return users.findById(userId).map(this::toPublic).orElseThrow();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void changePassword(UUID userId, ChangePasswordRequest req) {
        try {
            var hash = users.findPasswordHash(userId).orElseThrow();
            if (!passwords.matches(req.currentPassword(), hash)) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid current password");
            }
            users.updatePasswordHash(userId, passwords.hash(req.newPassword()));
            sessions.revokeAllForUser(userId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public RefreshSessionListResponse listSessions(UUID userId) {
        try {
            List<RefreshSessionPublic> items = sessions.listActiveByUser(userId).stream()
                    .map(s -> new RefreshSessionPublic(s.id(), s.createdAt(), s.expiresAt(), s.orgId(), s.deviceLabel()))
                    .toList();
            return new RefreshSessionListResponse(items);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void revokeSession(UUID userId, UUID sessionId) {
        try {
            if (!sessions.revokeIfOwned(sessionId, userId)) {
                throw new IamApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "session");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private UserPublic toPublic(UserRepository.UserRow u) {
        return new UserPublic(
                u.id(),
                u.email(),
                u.emailVerifiedAt() != null,
                u.displayName(),
                u.locale(),
                u.status());
    }
}

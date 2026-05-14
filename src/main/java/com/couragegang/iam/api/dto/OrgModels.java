package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrgModels {

    private OrgModels() {}

    @Serdeable
    public record Organization(UUID id, String name, String slug, @Nullable String planTier, Instant createdAt) {}

    @Serdeable
    public record OrganizationCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String slug,
            @Nullable String planTier) {}

    @Serdeable
    public record OrganizationPatchRequest(@Nullable String name, @Nullable String slug) {}

    @Serdeable
    public record Membership(
            UUID id,
            UUID userId,
            UUID orgId,
            String status,
            List<String> roles,
            @Nullable Instant joinedAt) {}

    @Serdeable
    public record MembershipPatchRequest(@Nullable String status, @Nullable List<String> roleKeys) {}

    @Serdeable
    public record MemberPage(List<Membership> items, @Nullable String nextCursor) {}

    @Serdeable
    public record Invite(
            UUID id,
            String email,
            @Nullable List<String> roleKeys,
            Instant expiresAt,
            Instant createdAt,
            @Nullable Instant acceptedAt) {}

    @Serdeable
    public record InviteCreateRequest(
            @NotBlank @Email String email,
            @NotEmpty List<String> roleKeys,
            @Nullable Integer ttlHours) {}

    @Serdeable
    public record InviteCreated(
            UUID id,
            String email,
            @Nullable List<String> roleKeys,
            Instant expiresAt,
            Instant createdAt,
            @Nullable Instant acceptedAt,
            @Nullable String acceptUrlHint) {}

    @Serdeable
    public record InviteListResponse(List<Invite> items) {}

    @Serdeable
    public record InviteAcceptRequest(@NotNull UUID orgId, @NotBlank String token) {}
}

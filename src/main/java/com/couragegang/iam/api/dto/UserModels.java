package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserModels {

    private UserModels() {}

    @Serdeable
    public record UserPublic(
            UUID id,
            String email,
            @Nullable Boolean emailVerified,
            @Nullable String displayName,
            @Nullable String locale,
            String status) {}

    @Serdeable
    public record MeOrgSummary(UUID orgId, String slug, String name, List<String> roles) {}

    @Serdeable
    public record MeResponse(UserPublic user, List<MeOrgSummary> organizations) {}

    @Serdeable
    public record MePatchRequest(@Nullable @Size(max = 200) String displayName, @Nullable @Size(max = 16) String locale) {}

    @Serdeable
    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank @Size(min = 10) String newPassword) {}

    @Serdeable
    public record RefreshSessionPublic(
            UUID id,
            Instant createdAt,
            Instant expiresAt,
            @Nullable UUID orgId,
            @Nullable String deviceLabel) {}

    @Serdeable
    public record RefreshSessionListResponse(List<RefreshSessionPublic> items) {}
}

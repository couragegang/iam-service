package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import java.util.UUID;

public final class IdpModels {

    private IdpModels() {}

    @Serdeable
    public record OrgIdpConfigPublic(
            @Nullable UUID id,
            @Nullable String type,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String metadataUrl,
            @Nullable Boolean enabled,
            @Nullable Boolean jitProvisioning,
            @Nullable String defaultRoleKey) {}

    @Serdeable
    public record OrgIdpConfigPatchRequest(
            @Nullable String type,
            @Nullable String issuer,
            @Nullable String clientId,
            @Nullable String clientSecret,
            @Nullable String metadataUrl,
            @Nullable Boolean enabled,
            @Nullable Boolean jitProvisioning,
            @Nullable String defaultRoleKey) {}

    @Serdeable
    public record OrgIdpTestRequest(@Nullable String metadataUrl) {}

    @Serdeable
    public record OrgIdpTestResult(boolean ok, @Nullable String message, @Nullable String issuerResolved) {}
}

package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class InternalModels {

    private InternalModels() {}

    @Serdeable
    public record IntrospectRequest(@NotBlank String token) {}

    @Serdeable
    public record IntrospectResponse(
            boolean active,
            @Nullable UUID sub,
            @Nullable UUID orgId,
            @Nullable String scope,
            @Nullable List<String> roles,
            @Nullable List<String> permissions,
            @Nullable Long exp) {}
}

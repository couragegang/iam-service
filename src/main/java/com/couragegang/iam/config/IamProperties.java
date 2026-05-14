package com.couragegang.iam.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Context
@ConfigurationProperties("iam")
public record IamProperties(
        @NotBlank String jwtSecret,
        @Min(60) int jwtAccessTtlSeconds,
        @Min(3600) int refreshTtlSeconds,
        @Nullable String oidcGoogleClientId,
        @Nullable String oidcGoogleClientSecret,
        @Nullable String oidcGoogleRedirectUri,
        @Nullable String oidcGithubClientId,
        @Nullable String oidcGithubClientSecret,
        @Nullable String oidcGithubRedirectUri) {

    public IamProperties {
        if (jwtSecret.length() < 32) {
            throw new IllegalArgumentException("iam.jwt-secret must be at least 32 characters");
        }
    }
}

package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthModels {

    private AuthModels() {}

    @Serdeable
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10) String password,
            @NotBlank @Size(max = 200) String displayName,
            @Nullable @Size(max = 200) String organizationName) {}

    @Serdeable
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    @Serdeable
    public record RefreshRequest(@NotBlank String refreshToken) {}

    @Serdeable
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    @Serdeable
    public record ResetPasswordRequest(
            @NotBlank String token, @NotBlank @Size(min = 10) String newPassword) {}

    @Serdeable
    public record VerifyEmailRequest(@NotBlank String token) {}

    @Serdeable
    public record SwitchOrgRequest(@NotNull UUID orgId) {}

    @Serdeable
    public record AuthTokensResponse(
            String accessToken,
            int accessExpiresIn,
            @Nullable String refreshToken,
            @Nullable Integer refreshExpiresIn,
            String tokenType) {}
}

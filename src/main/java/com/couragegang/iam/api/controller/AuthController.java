package com.couragegang.iam.api.controller;

import com.couragegang.iam.api.dto.AcceptedEmpty;
import com.couragegang.iam.api.dto.AuthModels.AuthTokensResponse;
import com.couragegang.iam.api.dto.AuthModels.ForgotPasswordRequest;
import com.couragegang.iam.api.dto.AuthModels.LoginRequest;
import com.couragegang.iam.api.dto.AuthModels.RefreshRequest;
import com.couragegang.iam.api.dto.AuthModels.RegisterRequest;
import com.couragegang.iam.api.dto.AuthModels.ResetPasswordRequest;
import com.couragegang.iam.api.dto.AuthModels.SwitchOrgRequest;
import com.couragegang.iam.api.dto.AuthModels.VerifyEmailRequest;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.security.SecurityAttributes;
import com.couragegang.iam.service.AuthService;
import com.couragegang.iam.service.OidcService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestAttribute;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Controller("/auth")
public final class AuthController {

    private final AuthService auth;
    private final OidcService oidc;

    public AuthController(AuthService auth, OidcService oidc) {
        this.auth = auth;
        this.oidc = oidc;
    }

    @Post("/register")
    public HttpResponse<AuthTokensResponse> register(@Body @Valid RegisterRequest body) {
        return HttpResponse.created(auth.register(body));
    }

    @Post("/login")
    public HttpResponse<AuthTokensResponse> login(HttpRequest<?> request, @Body @Valid LoginRequest body) {
        return HttpResponse.ok(auth.login(body, clientIp(request)));
    }

    @Post("/refresh")
    public HttpResponse<AuthTokensResponse> refresh(@Body @Valid RefreshRequest body) {
        return HttpResponse.ok(auth.refresh(body));
    }

    @Post("/logout")
    public HttpResponse<Void> logout(@Body @Valid RefreshRequest body) {
        auth.logout(body);
        return HttpResponse.noContent();
    }

    @Post("/forgot-password")
    public HttpResponse<AcceptedEmpty> forgotPassword(@Body @Valid ForgotPasswordRequest body) {
        auth.forgot(body);
        return HttpResponse.status(HttpStatus.ACCEPTED).body(new AcceptedEmpty(true));
    }

    @Post("/reset-password")
    public HttpResponse<Void> resetPassword(@Body @Valid ResetPasswordRequest body) {
        auth.reset(body);
        return HttpResponse.noContent();
    }

    @Post("/verify-email")
    public HttpResponse<Void> verifyEmail(@Body @Valid VerifyEmailRequest body) {
        auth.verify(body);
        return HttpResponse.noContent();
    }

    @Post("/switch-org")
    public HttpResponse<AuthTokensResponse> switchOrg(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @Body @Valid SwitchOrgRequest body) {
        return HttpResponse.ok(auth.switchOrg(body, UUID.fromString(userId)));
    }

    @Get("/oidc/{provider}/start")
    public HttpResponse<Void> oidcStart(
            @PathVariable String provider, @Nullable @QueryValue("redirect_after") String redirectAfter) {
        return HttpResponse.status(HttpStatus.FOUND).location(oidc.start(provider, redirectAfter));
    }

    @Get("/oidc/{provider}/callback")
    public HttpResponse<Void> oidcCallback(
            @PathVariable String provider,
            @Nullable @QueryValue String code,
            @Nullable @QueryValue String state) {
        return HttpResponse.status(HttpStatus.FOUND).location(oidc.callback(provider, code, state));
    }

    @Get("/sso/{orgSlug}/start")
    public HttpResponse<Void> ssoStart(@PathVariable String orgSlug) {
        throw new IamApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "SAML SSO is not implemented");
    }

    @Post("/sso/{orgSlug}/acs")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<Void> ssoSamlAcs(@PathVariable String orgSlug, @Body Map<String, String> form) {
        throw new IamApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "SAML SSO is not implemented");
    }

    private static InetAddress clientIp(HttpRequest<?> request) {
        var xf = request.getHeaders().get("X-Forwarded-For");
        if (xf.isPresent() && !xf.get().isBlank()) {
            var first = xf.get().split(",")[0].trim();
            try {
                return InetAddress.getByName(first);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return request.getRemoteAddress()
                .filter(a -> a instanceof InetSocketAddress)
                .map(a -> ((InetSocketAddress) a).getAddress())
                .orElseGet(() -> InetAddress.getLoopbackAddress());
    }
}

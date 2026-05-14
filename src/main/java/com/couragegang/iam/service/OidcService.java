package com.couragegang.iam.service;

import com.couragegang.iam.api.dto.AuthModels.AuthTokensResponse;
import com.couragegang.iam.config.IamProperties;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.metrics.OutboundHttpMetrics;
import com.couragegang.iam.repo.ExternalIdentityRepository;
import com.couragegang.iam.repo.OidcStateRepository;
import com.couragegang.iam.repo.UserRepository;
import com.couragegang.iam.security.PasswordHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Singleton
public final class OidcService {

    private static final SecureRandom RND = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IamProperties props;
    private final OidcStateRepository states;
    private final UserRepository users;
    private final ExternalIdentityRepository external;
    private final PasswordHasher passwords;
    private final AuthService authService;
    private final OutboundHttpMetrics outboundHttp;

    public OidcService(
            IamProperties props,
            OidcStateRepository states,
            UserRepository users,
            ExternalIdentityRepository external,
            PasswordHasher passwords,
            AuthService authService,
            OutboundHttpMetrics outboundHttp) {
        this.props = props;
        this.states = states;
        this.users = users;
        this.external = external;
        this.passwords = passwords;
        this.authService = authService;
        this.outboundHttp = outboundHttp;
    }

    public URI start(String provider, String redirectAfter) {
        var p = provider.toLowerCase(Locale.ROOT);
        var state = randomState();
        var exp = Instant.now().plus(10, ChronoUnit.MINUTES);
        try {
            states.insert(state, p, redirectAfter == null ? "" : redirectAfter, exp);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return switch (p) {
            case "google" -> googleAuthUrl(state);
            case "github" -> githubAuthUrl(state);
            default -> throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unsupported provider");
        };
    }

    public URI callback(String provider, String code, String state) {
        try {
            if (code == null || code.isBlank() || state == null || state.isBlank()) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "missing code or state");
            }
            var st = states.takeIfValid(state).orElseThrow(() -> new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid state"));
            if (!st.provider().equalsIgnoreCase(provider)) {
                throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "provider mismatch");
            }
            var provKey = "oidc:" + st.provider();
            var subj =
                    switch (st.provider()) {
                        case "google" -> googleUserSub(code);
                        case "github" -> githubUserSub(code);
                        default -> throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "unsupported provider");
                    };
            var uid = resolveOrCreateUser(subj, provKey);
            var tokens = authService.issueSession(uid, null);
            return redirectWithTokens(st.redirectAfter(), tokens);
        } catch (IamApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IamApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
        }
    }

    private UUID resolveOrCreateUser(OidcSubject subj, String providerKey) throws Exception {
        var email = subj.email().trim().toLowerCase(Locale.ROOT);
        var existing = users.findActiveIdByEmailLower(email);
        if (existing.isPresent()) {
            var uid = existing.get();
            external.link(uid, providerKey, subj.subject(), email);
            return uid;
        }
        var uid = users.insertUser(email, displayNameFromEmail(email), "ru");
        users.insertPassword(uid, passwords.hash(randomState() + randomState() + Long.toHexString(System.nanoTime())));
        external.link(uid, providerKey, subj.subject(), email);
        return uid;
    }

    private static String displayNameFromEmail(String emailLower) {
        var at = emailLower.indexOf('@');
        return at > 0 ? emailLower.substring(0, at) : emailLower;
    }

    private record OidcSubject(String subject, String email) {}

    private OidcSubject googleUserSub(String code) throws Exception {
        if (props.oidcGoogleClientId() == null
                || props.oidcGoogleClientId().isBlank()
                || props.oidcGoogleClientSecret() == null
                || props.oidcGoogleClientSecret().isBlank()) {
            throw new IamApiException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "google OIDC not configured");
        }
        var redirect = props.oidcGoogleRedirectUri();
        if (redirect == null || redirect.isBlank()) {
            throw new IamApiException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "google redirect uri not configured");
        }
        var form = "code="
                + url(code)
                + "&client_id="
                + url(props.oidcGoogleClientId())
                + "&client_secret="
                + url(props.oidcGoogleClientSecret())
                + "&redirect_uri="
                + url(redirect)
                + "&grant_type=authorization_code";
        var tokenReq = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var tokenResp = outboundHttp.send(HTTP, tokenReq, "oidc.google", "token");
        if (tokenResp.statusCode() >= 400) {
            throw new IllegalStateException("token " + tokenResp.statusCode());
        }
        var access = text(JSON.readTree(tokenResp.body()), "access_token");
        if (access == null || access.isBlank()) {
            throw new IllegalStateException("no access_token");
        }
        var uiReq = HttpRequest.newBuilder(URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
                .header("Authorization", "Bearer " + access)
                .GET()
                .build();
        var uiResp = outboundHttp.send(HTTP, uiReq, "oidc.google", "userinfo");
        if (uiResp.statusCode() >= 400) {
            throw new IllegalStateException("userinfo " + uiResp.statusCode());
        }
        var node = JSON.readTree(uiResp.body());
        var sub = text(node, "sub");
        var email = text(node, "email");
        if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
            throw new IllegalStateException("missing sub or email");
        }
        return new OidcSubject(sub, email);
    }

    private OidcSubject githubUserSub(String code) throws Exception {
        if (props.oidcGithubClientId() == null
                || props.oidcGithubClientId().isBlank()
                || props.oidcGithubClientSecret() == null
                || props.oidcGithubClientSecret().isBlank()) {
            throw new IamApiException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "github OIDC not configured");
        }
        var redirect = props.oidcGithubRedirectUri();
        if (redirect == null || redirect.isBlank()) {
            throw new IamApiException(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", "github redirect uri not configured");
        }
        var form = "code="
                + url(code)
                + "&client_id="
                + url(props.oidcGithubClientId())
                + "&client_secret="
                + url(props.oidcGithubClientSecret())
                + "&redirect_uri="
                + url(redirect);
        var tokenReq = HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var tokenResp = outboundHttp.send(HTTP, tokenReq, "oidc.github", "token");
        if (tokenResp.statusCode() >= 400) {
            throw new IllegalStateException("token " + tokenResp.statusCode());
        }
        var access = text(JSON.readTree(tokenResp.body()), "access_token");
        if (access == null || access.isBlank()) {
            throw new IllegalStateException("no access_token");
        }
        var userReq = HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                .header("Authorization", "Bearer " + access)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        var userResp = outboundHttp.send(HTTP, userReq, "oidc.github", "user");
        if (userResp.statusCode() >= 400) {
            throw new IllegalStateException("user " + userResp.statusCode());
        }
        var userNode = JSON.readTree(userResp.body());
        var id = text(userNode, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("missing github id");
        }
        var email = githubPrimaryEmail(access);
        return new OidcSubject(id, email);
    }

    private String githubPrimaryEmail(String accessToken) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("https://api.github.com/user/emails"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        var resp = outboundHttp.send(HTTP, req, "oidc.github", "user_emails");
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("emails " + resp.statusCode());
        }
        JsonNode arr = JSON.readTree(resp.body());
        if (!arr.isArray() || arr.isEmpty()) {
            throw new IllegalStateException("no github emails");
        }
        for (JsonNode n : arr) {
            if (n.path("primary").asBoolean(false) && n.path("verified").asBoolean(false)) {
                var em = text(n, "email");
                if (em != null && !em.isBlank()) {
                    return em;
                }
            }
        }
        for (JsonNode n : arr) {
            if (n.path("verified").asBoolean(false)) {
                var em = text(n, "email");
                if (em != null && !em.isBlank()) {
                    return em;
                }
            }
        }
        var fallback = text(arr.get(0), "email");
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("no usable github email");
        }
        return fallback;
    }

    private URI googleAuthUrl(String state) {
        var q = "client_id="
                + url(props.oidcGoogleClientId())
                + "&redirect_uri="
                + url(props.oidcGoogleRedirectUri())
                + "&response_type=code&scope="
                + url("openid email profile")
                + "&state="
                + url(state);
        return URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + q);
    }

    private URI githubAuthUrl(String state) {
        var q = "client_id="
                + url(props.oidcGithubClientId())
                + "&redirect_uri="
                + url(props.oidcGithubRedirectUri())
                + "&scope="
                + url("read:user user:email")
                + "&state="
                + url(state);
        return URI.create("https://github.com/login/oauth/authorize?" + q);
    }

    private static URI redirectWithTokens(String redirectAfter, AuthTokensResponse t) {
        var base = redirectAfter == null || redirectAfter.isBlank() ? "https://app.example.com/" : redirectAfter;
        var sep = base.contains("#") ? "&" : "#";
        var rt = t.refreshToken() == null ? "" : t.refreshToken();
        var sb = new StringBuilder(base)
                .append(sep)
                .append("access_token=")
                .append(url(t.accessToken()))
                .append("&refresh_token=")
                .append(url(rt))
                .append("&token_type=")
                .append(url(t.tokenType()))
                .append("&expires_in=")
                .append(t.accessExpiresIn());
        return URI.create(sb.toString());
    }

    private static String url(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String randomState() {
        var b = new byte[24];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String text(JsonNode node, String field) {
        var v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        var s = v.asText();
        return s.isBlank() ? null : s;
    }
}

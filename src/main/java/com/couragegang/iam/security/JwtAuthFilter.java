package com.couragegang.iam.security;

import com.couragegang.iam.security.JwtService.ParsedAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Singleton
@Filter("/**")
public class JwtAuthFilter implements HttpServerFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        var path = request.getPath();
        if (isPublic(path)) {
            return chain.proceed(request);
        }
        var auth = request.getHeaders().getAuthorization().orElse(null);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
        }
        var raw = auth.substring("Bearer ".length()).trim();
        try {
            ParsedAccess p = jwtService.parseAndVerify(raw);
            MutableHttpRequest<?> mut = request.mutate();
            mut.setAttribute(SecurityAttributes.USER_ID, p.userId().toString());
            if (p.orgId() != null) {
                mut.setAttribute(SecurityAttributes.ORG_ID, p.orgId().toString());
            }
            return chain.proceed(mut);
        } catch (Exception e) {
            return Mono.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
        }
    }

    private static boolean isPublic(String path) {
        var p = path.startsWith("/v1/iam") ? path.substring("/v1/iam".length()) : path;
        if (p.isEmpty()) {
            p = "/";
        }
        return p.equals("/")
                || p.startsWith("/health")
                || p.startsWith("/metrics")
                || p.startsWith("/prometheus")
                || p.startsWith("/swagger")
                || p.startsWith("/auth/register")
                || p.startsWith("/auth/login")
                || p.startsWith("/auth/refresh")
                || p.startsWith("/auth/logout")
                || p.startsWith("/auth/forgot-password")
                || p.startsWith("/auth/reset-password")
                || p.startsWith("/auth/verify-email")
                || p.startsWith("/auth/oidc/")
                || p.startsWith("/auth/sso/")
                || p.startsWith("/internal/");
    }
}

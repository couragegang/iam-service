package com.couragegang.iam.security;

import com.couragegang.iam.security.JwtService.ParsedAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

@Singleton
@Filter("/**")
public final class JwtAuthFilter implements HttpServerFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Publisher<? extends io.micronaut.http.MutableHttpResponse<?>> doFilter(
            HttpRequest<?> request, ServerFilterChain chain) {
        var path = request.getPath();
        if (isPublic(path)) {
            return chain.proceed(request);
        }
        var auth = request.getHeaders().getAuthorization();
        if (auth.isEmpty() || !auth.get().startsWith("Bearer ")) {
            return io.micronaut.core.async.publisher.Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
        }
        var raw = auth.get().substring("Bearer ".length()).trim();
        try {
            ParsedAccess p = jwtService.parseAndVerify(raw);
            MutableHttpRequest<?> mut = request.mutate();
            mut.setAttribute(SecurityAttributes.USER_ID, p.userId().toString());
            if (p.orgId() != null) {
                mut.setAttribute(SecurityAttributes.ORG_ID, p.orgId().toString());
            }
            return chain.proceed(mut);
        } catch (Exception e) {
            return io.micronaut.core.async.publisher.Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
        }
    }

    private static boolean isPublic(String path) {
        return path.equals("/")
                || path.startsWith("/health")
                || path.startsWith("/metrics")
                || path.startsWith("/prometheus")
                || path.startsWith("/swagger")
                || path.startsWith("/auth/register")
                || path.startsWith("/auth/login")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/logout")
                || path.startsWith("/auth/forgot-password")
                || path.startsWith("/auth/reset-password")
                || path.startsWith("/auth/verify-email")
                || path.startsWith("/auth/oidc/")
                || path.startsWith("/auth/sso/")
                || path.startsWith("/internal/");
    }
}

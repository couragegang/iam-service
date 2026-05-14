package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.security.JwtService.ParsedAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ServerFilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

final class JwtAuthFilterTest {

    private JwtService jwt;
    private JwtAuthFilter filter;
    private ServerFilterChain chain;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        filter = new JwtAuthFilter(jwt);
        chain = mock(ServerFilterChain.class);
    }

    @Test
    void publicPathSkipsAuth() throws Exception {
        var req = mock(HttpRequest.class);
        when(req.getPath()).thenReturn("/auth/login");
        when(chain.proceed(req)).thenReturn(io.micronaut.core.async.publisher.Publishers.just(HttpResponse.ok()));
        var out = blockOne(filter.doFilter(req, chain));
        assertThat(out.getStatus()).isEqualTo(HttpStatus.OK);
        verify(jwt, never()).parseAndVerify(any());
    }

    @Test
    void metricsPathSkipsAuth() throws Exception {
        var req = mock(HttpRequest.class);
        when(req.getPath()).thenReturn("/metrics");
        when(chain.proceed(req)).thenReturn(io.micronaut.core.async.publisher.Publishers.just(HttpResponse.ok()));
        var out = blockOne(filter.doFilter(req, chain));
        assertThat(out.getStatus()).isEqualTo(HttpStatus.OK);
        verify(jwt, never()).parseAndVerify(any());
    }

    @Test
    void missingBearerReturns401() throws Exception {
        var req = mock(HttpRequest.class);
        when(req.getPath()).thenReturn("/me");
        when(req.getHeaders().getAuthorization()).thenReturn(Optional.empty());
        var out = blockOne(filter.doFilter(req, chain));
        assertThat(out.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).proceed(any());
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        var req = mock(HttpRequest.class);
        when(req.getPath()).thenReturn("/me");
        when(req.getHeaders().getAuthorization()).thenReturn(Optional.of("Bearer x"));
        when(jwt.parseAndVerify("x")).thenThrow(new RuntimeException("bad"));
        var out = blockOne(filter.doFilter(req, chain));
        assertThat(out.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validJwtProceedsWithAttributes() throws Exception {
        var uid = UUID.randomUUID();
        var org = UUID.randomUUID();
        var req = mock(HttpRequest.class);
        var mut = mock(MutableHttpRequest.class);
        when(req.getPath()).thenReturn("/organizations");
        when(req.getHeaders().getAuthorization()).thenReturn(Optional.of("Bearer tok"));
        when(jwt.parseAndVerify("tok"))
                .thenReturn(new ParsedAccess(uid, org, List.of("owner"), Instant.now().plusSeconds(600)));
        when(req.mutate()).thenReturn(mut);
        when(chain.proceed(mut)).thenReturn(io.micronaut.core.async.publisher.Publishers.just(HttpResponse.ok("ok")));
        var out = blockOne(filter.doFilter(req, chain));
        assertThat(out.getStatus()).isEqualTo(HttpStatus.OK);
        verify(mut).setAttribute(SecurityAttributes.USER_ID, uid.toString());
        verify(mut).setAttribute(SecurityAttributes.ORG_ID, org.toString());
    }

    @Test
    void validJwtWithoutOrgSkipsOrgAttribute() throws Exception {
        var uid = UUID.randomUUID();
        var req = mock(HttpRequest.class);
        var mut = mock(MutableHttpRequest.class);
        when(req.getPath()).thenReturn("/me");
        when(req.getHeaders().getAuthorization()).thenReturn(Optional.of("Bearer t2"));
        when(jwt.parseAndVerify("t2"))
                .thenReturn(new ParsedAccess(uid, null, List.of(), Instant.now().plusSeconds(600)));
        when(req.mutate()).thenReturn(mut);
        when(chain.proceed(mut)).thenReturn(io.micronaut.core.async.publisher.Publishers.just(HttpResponse.ok()));
        blockOne(filter.doFilter(req, chain));
        verify(mut).setAttribute(SecurityAttributes.USER_ID, uid.toString());
        verify(mut, never()).setAttribute(eq(SecurityAttributes.ORG_ID), anyString());
    }

    private static HttpResponse<?> blockOne(org.reactivestreams.Publisher<? extends io.micronaut.http.MutableHttpResponse<?>> pub)
            throws Exception {
        var q = new LinkedBlockingQueue<HttpResponse<?>>(1);
        pub.subscribe(
                new Subscriber<>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onNext(io.micronaut.http.MutableHttpResponse<?> r) {
                        q.add(r);
                    }

                    @Override
                    public void onError(Throwable t) {
                        q.add(HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR));
                    }

                    @Override
                    public void onComplete() {}
                });
        var got = q.poll(5, TimeUnit.SECONDS);
        assertThat(got).isNotNull();
        return got;
    }
}

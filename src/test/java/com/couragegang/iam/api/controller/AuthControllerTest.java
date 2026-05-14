package com.couragegang.iam.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.api.dto.AuthModels.LoginRequest;
import com.couragegang.iam.api.dto.AuthModels.SwitchOrgRequest;
import com.couragegang.iam.error.IamApiException;
import com.couragegang.iam.service.AuthService;
import com.couragegang.iam.service.OidcService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AuthControllerTest {

    @Mock
    AuthService auth;

    @Mock
    OidcService oidc;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(auth, oidc);
    }

    @Test
    void loginUsesForwardedFor() throws Exception {
        var req = mock(HttpRequest.class, Answers.RETURNS_DEEP_STUBS);
        when(req.getHeaders().get("X-Forwarded-For")).thenReturn(Optional.of("10.0.0.9, 10.0.0.1"));
        when(auth.login(any(), eq(InetAddress.getByName("10.0.0.9")))).thenReturn(null);
        controller.login(req, new LoginRequest("a@b.co", "pw"));
        verify(auth).login(any(), eq(InetAddress.getByName("10.0.0.9")));
    }

    @Test
    void loginInvalidForwardedForFallsBackToRemote() throws Exception {
        var req = mock(HttpRequest.class, Answers.RETURNS_DEEP_STUBS);
        when(req.getHeaders().get("X-Forwarded-For")).thenReturn(Optional.of("not-an-ip"));
        when(req.getRemoteAddress()).thenReturn(Optional.of(new InetSocketAddress("192.168.1.2", 4444)));
        when(auth.login(any(), eq(InetAddress.getByName("192.168.1.2")))).thenReturn(null);
        controller.login(req, new LoginRequest("a@b.co", "pw"));
    }

    @Test
    void loginNoForwardedUsesRemoteOrLoopback() throws Exception {
        var req = mock(HttpRequest.class, Answers.RETURNS_DEEP_STUBS);
        when(req.getHeaders().get("X-Forwarded-For")).thenReturn(Optional.empty());
        when(req.getRemoteAddress()).thenReturn(Optional.empty());
        when(auth.login(any(), eq(InetAddress.getLoopbackAddress()))).thenReturn(null);
        controller.login(req, new LoginRequest("a@b.co", "pw"));
    }

    @Test
    void oidcStartDelegates() {
        when(oidc.start("google", null)).thenReturn(java.net.URI.create("https://idp"));
        var resp = controller.oidcStart("google", null);
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.FOUND);
        assertThat(resp.getHeaders().get("Location")).hasValue("https://idp");
    }

    @Test
    void ssoThrowsNotImplemented() {
        assertThatThrownBy(() -> controller.ssoStart("acme"))
                .isInstanceOf(IamApiException.class)
                .matches(ex -> ((IamApiException) ex).status() == HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void switchOrgPassesUserId() {
        var uid = UUID.randomUUID();
        when(auth.switchOrg(any(), eq(uid))).thenReturn(null);
        controller.switchOrg(uid.toString(), new SwitchOrgRequest(UUID.randomUUID()));
        verify(auth).switchOrg(any(), eq(uid));
    }
}

package com.couragegang.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.couragegang.iam.api.dto.UserModels.ChangePasswordRequest;
import com.couragegang.iam.api.dto.UserModels.MePatchRequest;
import com.couragegang.iam.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class MeControllerTest {

    @Mock
    UserService users;

    @Test
    void delegatesToService() throws Exception {
        var c = new MeController(users);
        var uid = UUID.randomUUID().toString();
        var uuid = UUID.fromString(uid);
        when(users.me(eq(uuid))).thenReturn(null);
        when(users.patch(eq(uuid), any())).thenReturn(null);
        when(users.listSessions(eq(uuid))).thenReturn(null);
        c.meGet(uid);
        verify(users).me(eq(uuid));
        c.mePatch(uid, new MePatchRequest("N", "en"));
        verify(users).patch(eq(uuid), any(MePatchRequest.class));
        c.meChangePassword(uid, new ChangePasswordRequest("a", "bbbbbbbbbb"));
        verify(users).changePassword(eq(uuid), any(ChangePasswordRequest.class));
        c.meListSessions(uid);
        verify(users).listSessions(eq(uuid));
        var sid = UUID.randomUUID();
        c.meRevokeSession(uid, sid);
        verify(users).revokeSession(eq(uuid), eq(sid));
    }
}

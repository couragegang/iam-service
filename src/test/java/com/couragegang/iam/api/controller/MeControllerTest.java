package com.couragegang.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void delegatesToService() {
        var c = new MeController(users);
        var uid = UUID.randomUUID().toString();
        var uuid = UUID.fromString(uid);
        c.meGet(uid);
        verify(users).me(uuid);
        c.mePatch(uid, new MePatchRequest("N", "en"));
        verify(users).patch(uuid, any(MePatchRequest.class));
        c.meChangePassword(uid, new ChangePasswordRequest("a", "bbbbbbbbbb"));
        verify(users).changePassword(uuid, any(ChangePasswordRequest.class));
        c.meListSessions(uid);
        verify(users).listSessions(uuid);
        var sid = UUID.randomUUID();
        c.meRevokeSession(uid, sid);
        verify(users).revokeSession(uuid, sid);
    }
}

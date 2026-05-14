package com.couragegang.iam.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.couragegang.iam.api.dto.OrgModels.InviteAcceptRequest;
import com.couragegang.iam.service.InviteService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class InvitesControllerTest {

    @Mock
    InviteService invites;

    @Test
    void acceptDelegates() {
        var c = new InvitesController(invites);
        var uid = UUID.randomUUID().toString();
        var body = new InviteAcceptRequest(UUID.randomUUID(), "tok");
        c.invitesAccept(uid, body);
        verify(invites).accept(UUID.fromString(uid), body);
    }
}

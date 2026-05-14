package com.couragegang.iam.api.controller;

import com.couragegang.iam.api.dto.OrgModels.InviteAcceptRequest;
import com.couragegang.iam.api.dto.OrgModels.Membership;
import com.couragegang.iam.security.SecurityAttributes;
import com.couragegang.iam.service.InviteService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/invites")
public final class InvitesController {

    private final InviteService invites;

    public InvitesController(InviteService invites) {
        this.invites = invites;
    }

    @Post("/accept")
    public HttpResponse<Membership> invitesAccept(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @Body @Valid InviteAcceptRequest body) {
        return HttpResponse.ok(invites.accept(UUID.fromString(userId), body));
    }
}

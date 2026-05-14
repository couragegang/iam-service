package com.couragegang.iam.api.controller;

import com.couragegang.iam.api.dto.UserModels.ChangePasswordRequest;
import com.couragegang.iam.api.dto.UserModels.MePatchRequest;
import com.couragegang.iam.api.dto.UserModels.MeResponse;
import com.couragegang.iam.api.dto.UserModels.RefreshSessionListResponse;
import com.couragegang.iam.api.dto.UserModels.UserPublic;
import com.couragegang.iam.security.SecurityAttributes;
import com.couragegang.iam.service.UserService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/me")
public final class MeController {

    private final UserService users;

    public MeController(UserService users) {
        this.users = users;
    }

    @Get
    public HttpResponse<MeResponse> meGet(@RequestAttribute(SecurityAttributes.USER_ID) String userId) {
        return HttpResponse.ok(users.me(UUID.fromString(userId)));
    }

    @Patch
    public HttpResponse<UserPublic> mePatch(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @Body @Valid MePatchRequest body) {
        return HttpResponse.ok(users.patch(UUID.fromString(userId), body));
    }

    @Post("/password")
    public HttpResponse<Void> meChangePassword(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @Body @Valid ChangePasswordRequest body) {
        users.changePassword(UUID.fromString(userId), body);
        return HttpResponse.noContent();
    }

    @Get("/sessions")
    public HttpResponse<RefreshSessionListResponse> meListSessions(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId) {
        return HttpResponse.ok(users.listSessions(UUID.fromString(userId)));
    }

    @Delete("/sessions/{sessionId}")
    public HttpResponse<Void> meRevokeSession(
            @RequestAttribute(SecurityAttributes.USER_ID) String userId, @PathVariable UUID sessionId) {
        users.revokeSession(UUID.fromString(userId), sessionId);
        return HttpResponse.noContent();
    }
}

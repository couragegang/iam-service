package com.couragegang.iam.api.controller;

import com.couragegang.iam.api.dto.InternalModels.IntrospectRequest;
import com.couragegang.iam.api.dto.InternalModels.IntrospectResponse;
import com.couragegang.iam.service.IntrospectService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

@Controller("/internal")
public class InternalController {

    private final IntrospectService introspect;

    public InternalController(IntrospectService introspect) {
        this.introspect = introspect;
    }

    @Post("/token/introspect")
    public HttpResponse<IntrospectResponse> internalTokenIntrospect(@Body @Valid IntrospectRequest body) {
        return HttpResponse.ok(introspect.introspect(body));
    }
}

package com.couragegang.iam.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.iam.api.dto.InternalModels.IntrospectRequest;
import com.couragegang.iam.api.dto.InternalModels.IntrospectResponse;
import com.couragegang.iam.service.IntrospectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class InternalControllerTest {

    @Mock
    IntrospectService introspect;

    @Test
    void introspectDelegates() {
        var c = new InternalController(introspect);
        var req = new IntrospectRequest("t");
        when(introspect.introspect(req)).thenReturn(new IntrospectResponse(false, null, null, null, null, null, null));
        c.internalTokenIntrospect(req);
        verify(introspect).introspect(req);
    }
}

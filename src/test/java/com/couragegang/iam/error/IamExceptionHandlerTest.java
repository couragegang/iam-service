package com.couragegang.iam.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

final class IamExceptionHandlerTest {

    private final IamExceptionHandler handler = new IamExceptionHandler();

    @Test
    void mapsExceptionToResponse() {
        var ex = new IamApiException(HttpStatus.BAD_REQUEST, "CODE", "hello");
        var resp = handler.handle(mock(HttpRequest.class), ex);
        assertThat(resp.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(resp.body()).isNotNull();
        assertThat(resp.body().code()).isEqualTo("CODE");
        assertThat(resp.body().message()).isEqualTo("hello");
    }
}

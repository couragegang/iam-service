package com.couragegang.iam.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

final class IamApiExceptionTest {

    @Test
    void exposesStatusAndBody() {
        var ex = new IamApiException(HttpStatus.CONFLICT, "CONFLICT", "msg");
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.status().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(ex.body().code()).isEqualTo("CONFLICT");
        assertThat(ex.body().message()).isEqualTo("msg");
    }
}

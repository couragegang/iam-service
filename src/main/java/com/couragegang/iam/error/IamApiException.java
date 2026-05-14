package com.couragegang.iam.error;

import com.couragegang.iam.api.dto.ErrorBody;
import io.micronaut.http.HttpStatus;

public final class IamApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorBody body;

    public IamApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.body = ErrorBody.of(code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorBody body() {
        return body;
    }
}

package com.couragegang.iam.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
public record ErrorBody(String code, String message, Map<String, Object> details) {

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(code, message, null);
    }
}

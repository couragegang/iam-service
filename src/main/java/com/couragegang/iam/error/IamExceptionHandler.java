package com.couragegang.iam.error;

import com.couragegang.iam.api.dto.ErrorBody;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
public class IamExceptionHandler implements ExceptionHandler<IamApiException, HttpResponse<ErrorBody>> {

    @Override
    public HttpResponse<ErrorBody> handle(HttpRequest request, IamApiException exception) {
        return HttpResponse.status(exception.status()).body(exception.body());
    }
}

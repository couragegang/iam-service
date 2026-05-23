package com.couragegang.iam.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;

/**
 * Простой маркер, что сервис поднят (дополнительно к встроенному /health).
 */
@Controller
public class HealthInfoController {

    @Get("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "iam-service",
                "openapiUi", "/v1/iam/swagger/views/swagger-ui/index.html",
                "health", "/v1/iam/health",
                "metrics", "/v1/iam/prometheus"
        );
    }
}

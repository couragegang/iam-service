package com.couragegang.iam.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ConfigWorkspaceClient {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigWorkspaceClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String internalKey;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ConfigWorkspaceClient(
            @Value("${iam.config-service.enabled:true}") boolean enabled,
            @Value("${iam.config-service.base-url:http://localhost:8084/v1/config}") String baseUrl,
            @Value("${iam.config-service.internal-api-key:dev-internal-key}") String internalKey) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalKey = internalKey;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Optional<UUID> bootstrapDefaultWorkspace(UUID orgId, UUID defaultGroupId, String orgName) {
        if (!enabled) {
            LOG.debug("config-service integration disabled");
            return Optional.empty();
        }
        try {
            var body =
                    json.writeValueAsString(
                            new BootstrapBody(defaultGroupId, orgName));
            var uri = URI.create(baseUrl + "/internal/orgs/" + orgId + "/bootstrap-default-workspace");
            var request =
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("X-Config-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var tree = json.readTree(response.body());
                if (tree.hasNonNull("id")) {
                    return Optional.of(UUID.fromString(tree.get("id").asText()));
                }
            }
            LOG.warn(
                    "config-service bootstrap failed: status={} body={}",
                    response.statusCode(),
                    response.body());
        } catch (Exception e) {
            LOG.warn("config-service bootstrap error for orgId={}: {}", orgId, e.toString());
        }
        return Optional.empty();
    }

    private record BootstrapBody(UUID defaultGroupId, String orgName) {}
}

package com.couragegang.iam.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.couragegang.iam.metrics.OutboundHttpMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigWorkspaceClientTest {

    HttpServer server;
    int port;
    OutboundHttpMetrics metrics;

    private ConfigWorkspaceClient client(boolean enabled, String baseUrl, String key) {
        return new ConfigWorkspaceClient(enabled, baseUrl, key, metrics);
    }

    @BeforeEach
    void start() throws Exception {
        metrics = new OutboundHttpMetrics(new SimpleMeterRegistry());
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disabledReturnsEmpty() {
        var client = client(false, "http://localhost:" + port, "key");
        assertThat(client.bootstrapDefaultWorkspace(UUID.randomUUID(), UUID.randomUUID(), "Org"))
                .isEmpty();
    }

    @Test
    void bootstrapReturnsWorkspaceId() {
        var orgId = UUID.randomUUID();
        var wsId = UUID.randomUUID();
        server.createContext(
                "/v1/config/internal/orgs/" + orgId + "/bootstrap-default-workspace",
                exchange -> {
                    var body = "{\"id\":\"" + wsId + "\"}";
                    exchange.sendResponseHeaders(200, body.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        var client = client(true, "http://127.0.0.1:" + port + "/v1/config", "dev-internal-key");

        assertThat(client.bootstrapDefaultWorkspace(orgId, UUID.randomUUID(), "Acme")).contains(wsId);
    }

    @Test
    void bootstrapFailureReturnsEmpty() {
        var orgId = UUID.randomUUID();
        server.createContext(
                "/v1/config/internal/orgs/" + orgId + "/bootstrap-default-workspace",
                exchange -> exchange.sendResponseHeaders(500, -1));

        var client = client(true, "http://127.0.0.1:" + port + "/v1/config", "key");

        assertThat(client.bootstrapDefaultWorkspace(orgId, UUID.randomUUID(), "Acme")).isEmpty();
    }
}

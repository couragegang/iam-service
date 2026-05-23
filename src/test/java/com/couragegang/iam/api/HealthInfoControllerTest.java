package com.couragegang.iam.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class HealthInfoControllerTest {

    @Test
    void rootContainsServiceName() {
        var body = new HealthInfoController().root();
        assertThat(body.get("service")).isEqualTo("iam-service");
        assertThat(body.get("metrics")).isEqualTo("/v1/iam/prometheus");
    }
}

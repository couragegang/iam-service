package com.couragegang.iam.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class IamPropertiesTest {

    @Test
    void rejectsShortJwtSecret() {
        assertThatThrownBy(() -> new IamProperties("short", 60, 3600, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt-secret");
    }

    @Test
    void acceptsMinimalValidSecret() {
        new IamProperties("x".repeat(32), 60, 3600, null, null, null, null, null, null);
    }
}

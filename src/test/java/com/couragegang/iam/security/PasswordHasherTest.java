package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashAndMatch() {
        var h = hasher.hash("my-password-123");
        assertThat(hasher.matches("my-password-123", h)).isTrue();
        assertThat(hasher.matches("other", h)).isFalse();
    }
}

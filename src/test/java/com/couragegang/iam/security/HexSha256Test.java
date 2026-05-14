package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class HexSha256Test {

    @Test
    void hashUtf8Deterministic() {
        var a = HexSha256.hashUtf8("hello");
        var b = HexSha256.hashUtf8("hello");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void hashBytes() {
        assertThat(HexSha256.hashBytes(new byte[] {0, 1, 2})).hasSize(64);
    }
}

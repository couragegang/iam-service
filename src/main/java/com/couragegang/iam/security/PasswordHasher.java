package com.couragegang.iam.security;

import jakarta.inject.Singleton;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Singleton
public final class PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}

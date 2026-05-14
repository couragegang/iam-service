package com.couragegang.iam;

public final class TestSecrets {

    /** 64 hex-символа: удовлетворяет IamProperties (≥32) и используется в JWT. */
    public static final String JWT_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private TestSecrets() {}
}

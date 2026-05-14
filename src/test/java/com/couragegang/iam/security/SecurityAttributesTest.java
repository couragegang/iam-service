package com.couragegang.iam.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class SecurityAttributesTest {

    @Test
    void attributeNamesStable() {
        assertThat(SecurityAttributes.USER_ID).isEqualTo("iam.userId");
        assertThat(SecurityAttributes.ORG_ID).isEqualTo("iam.orgId");
    }
}

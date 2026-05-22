plugins {
    id("io.micronaut.application") version "4.5.4"
    id("com.gradleup.shadow") version "8.3.7"
    jacoco
}

extra["jacocoCoverageExcludes"] = listOf(
    "**/api/dto/**",
    "**/repo/**",
    "**/Application.class",
    "**/service/OidcService.class",
    "**/api/controller/**",
    "**/metrics/**",
)
apply(from = rootDir.resolve("gradle/jacoco-coverage.gradle.kts"))

version = "0.1.0-SNAPSHOT"
group = "com.couragegang.iam"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

micronaut {
    version("4.7.6")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        module("com.couragegang.iam")
    }
}

dependencies {
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation("io.projectreactor:reactor-core")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("jakarta.annotation:jakarta.annotation-api")

    compileOnly("io.micronaut.openapi:micronaut-openapi-annotations")

    implementation("io.micronaut.sql:micronaut-jdbc")
    implementation("io.micronaut:micronaut-http-client")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("org.springframework.security:spring-security-crypto:6.3.4")
    implementation("commons-logging:commons-logging:1.3.4")

    runtimeOnly("org.yaml:snakeyaml")
    implementation("org.postgresql:postgresql")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INHERIT
    from(layout.projectDirectory.dir("openapi")) {
        into("META-INF/swagger")
    }
}

application {
    mainClass.set("com.couragegang.iam.Application")
}

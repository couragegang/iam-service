# Сборка и запуск только в Docker (локально JVM не требуется).
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY openapi ./openapi
COPY src ./src

RUN gradle --no-daemon clean shadowJar -x test \
    && JAR=$(ls build/libs/*-all.jar | head -n1) \
    && test -n "$JAR" \
    && cp "$JAR" /app/iam-service.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/iam-service.jar /app/iam-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/iam-service.jar"]

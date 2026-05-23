# Сборка и запуск только в Docker (локально JVM не требуется).
# Секреты не в образе — runtime через platform/build/runtime.env (см. platform/config/contours).
ARG DEPLOY_CONTOUR=local

FROM gradle:8.10.2-jdk21 AS build
ARG DEPLOY_CONTOUR
WORKDIR /app

COPY gradle.properties settings.gradle.kts build.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
COPY openapi ./openapi
COPY src ./src

RUN gradle --no-daemon clean shadowJar -x test \
    && JAR=$(ls build/libs/*-all.jar | head -n1) \
    && test -n "$JAR" \
    && cp "$JAR" /app/iam-service.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG DEPLOY_CONTOUR
ENV DEPLOY_CONTOUR=${DEPLOY_CONTOUR}
LABEL org.opencontainers.image.contour="${DEPLOY_CONTOUR}"
WORKDIR /app
COPY --from=build /app/iam-service.jar /app/iam-service.jar
EXPOSE 8080

# VPS: docker build --target baked (см. platform/scripts/prepare-baked-build.sh)
FROM runtime AS baked
COPY docker/entrypoint-baked.sh /entrypoint-baked.sh
COPY docker/runtime-baked.env /app/config/runtime-baked.env
RUN chmod +x /entrypoint-baked.sh
ENTRYPOINT ["/entrypoint-baked.sh"]

FROM runtime AS local
ENTRYPOINT ["java", "-jar", "/app/iam-service.jar"]

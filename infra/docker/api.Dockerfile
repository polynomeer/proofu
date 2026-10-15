# syntax=docker/dockerfile:1
# ProofU API. Build context is the repository root; the jar is built before this runs
# (scripts/images.sh, .github/workflows/release.yml) because CI has already compiled and tested
# it by then — a Gradle build inside the image would download the world a second time.
#
#   ./gradlew :api:bootJar
#   docker build -f infra/docker/api.Dockerfile -t proofu-api .
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${JRE_IMAGE} AS layers
ARG JAR=apps/api/build/libs/api-0.1.0-SNAPSHOT.jar
COPY ${JAR} /app.jar
# Spring Boot's layers change at different rates: dependencies stay put while the application
# layer changes every commit, so a redeploy pushes and pulls only the last one.
RUN java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /layers

FROM ${JRE_IMAGE}
RUN groupadd --system --gid 10001 proofu && \
    useradd --system --uid 10001 --gid proofu --home-dir /app --shell /usr/sbin/nologin proofu
WORKDIR /app
COPY --from=layers --chown=proofu:proofu /layers/dependencies/ ./
COPY --from=layers --chown=proofu:proofu /layers/spring-boot-loader/ ./
COPY --from=layers --chown=proofu:proofu /layers/snapshot-dependencies/ ./
COPY --from=layers --chown=proofu:proofu /layers/application/ ./
USER proofu:proofu

# The container's memory limit is the only budget the JVM gets; exit instead of thrashing.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Deployments set SPRING_PROFILES_ACTIVE=production; without it the local profile would start
# with header auth and the fake AI provider (docs/operations/deployment.md).
ENV PORT=8080
EXPOSE 8080

# Readiness, not liveness: the api migrates the schema before it serves traffic.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=10 \
    CMD curl -fsS "http://127.0.0.1:${MANAGEMENT_SERVER_PORT:-$PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

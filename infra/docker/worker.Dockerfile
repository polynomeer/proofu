# syntax=docker/dockerfile:1
# ProofU worker (jobs table poller). Build context is the repository root; the jar is
# built before this runs
# (scripts/images.sh, .github/workflows/release.yml) because CI has already compiled and tested
# it by then — a Gradle build inside the image would download the world a second time.
#
#   ./gradlew :worker:bootJar
#   docker build -f infra/docker/worker.Dockerfile -t proofu-worker .
ARG JRE_IMAGE=eclipse-temurin:21-jre

FROM ${JRE_IMAGE} AS layers
ARG JAR=apps/worker/build/libs/worker-0.1.0-SNAPSHOT.jar
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
# with the fake AI provider (docs/operations/deployment.md). The worker must not start before
# the api of the same release has migrated the schema.
ENV PORT=8090
EXPOSE 8090

# The worker serves health and the metrics scrape only; it never migrates.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=6 \
    CMD curl -fsS "http://127.0.0.1:${MANAGEMENT_SERVER_PORT:-$PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

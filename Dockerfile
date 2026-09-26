# syntax=docker/dockerfile:1.7
#
# Production image for the DIYnCrafts backend: one image for both runtime roles
# (SPRING_PROFILES_ACTIVE=prod,api or prod,worker). It contains no credentials; all configuration
# comes from environment variables at run time (docs/deployment/ENVIRONMENTS.md).
#
# Base images are pinned by digest for reproducible builds; Dependabot proposes updates.
ARG JDK_IMAGE=eclipse-temurin:17-jdk-noble@sha256:e213bb3f008362fcab6ab1fdf182e2170ef633ffa8543d8fd2f29e99f768aa60
ARG JRE_IMAGE=eclipse-temurin:17-jre-noble@sha256:f7537fa73fa7c5bc4e51ea11a611c1d0d8bbf4e9f22de93b666072de98e752e2

# ---- build: compile and package with the Maven wrapper (tests run in CI before the image is built) ----
FROM ${JDK_IMAGE} AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/webapp-*.jar extract --layers --launcher --destination extracted

# ---- runtime: JRE + ffmpeg/ffprobe, non-root ----
FROM ${JRE_IMAGE} AS runtime
ARG REVISION=unknown
ARG VERSION=0.0.1-SNAPSHOT

# ffmpeg 6.1 from Ubuntu 24.04 (libx264 + AAC). Fail the build if a required capability is missing.
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ffmpeg \
 && rm -rf /var/lib/apt/lists/* \
 && ffprobe -hide_banner -version > /dev/null \
 && ffmpeg -hide_banner -encoders | grep -q ' libx264 ' \
 && ffmpeg -hide_banner -encoders | grep -q ' aac '

# Fixed, unprivileged UID/GID so volume ownership is predictable on every host.
RUN groupadd --system --gid 10001 diyncrafts \
 && useradd --system --uid 10001 --gid diyncrafts --no-create-home --home-dir /app --shell /usr/sbin/nologin diyncrafts \
 && mkdir -p /app /var/lib/diyncrafts/work \
 && chown -R 10001:10001 /var/lib/diyncrafts

WORKDIR /app
# Layers from least to most frequently changing, for smaller pulls on redeploy.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

LABEL org.opencontainers.image.title="diyncrafts-backend" \
      org.opencontainers.image.description="DIYnCrafts API and transcoding worker (Spring Boot, ffmpeg)" \
      org.opencontainers.image.source="https://github.com/RCGCHANDU/diyncrafts" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.version="${VERSION}"

# - Scratch space (uploads, ffmpeg output) lives on /var/lib/diyncrafts: mount a volume there. The
#   application directory stays read-only, so the root filesystem can be mounted read-only
#   (with a tmpfs on /tmp).
# - The JVM sizes its heap from the container memory limit; ffmpeg runs as a separate process in the
#   same memory limit, so the worker role lowers MaxRAMPercentage (see deploy/compose/compose.yaml).
ENV APP_TRANSCODING_WORK_DIR=/var/lib/diyncrafts/work \
    INFO_APP_REVISION=${REVISION} \
    INFO_APP_VERSION=${VERSION} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

USER 10001:10001
EXPOSE 8080

# Readiness (not liveness): the container is "healthy" once it may receive traffic. Platforms that
# restart unhealthy containers should probe /actuator/health/liveness for that decision instead.
HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

# The JVM is PID 1 and handles SIGTERM with a graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

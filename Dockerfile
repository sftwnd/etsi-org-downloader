# syntax=docker/dockerfile:1

# Build stage: the Maven Wrapper downloads the Maven of the project and checks the SHA-256 of its zip distribution.
# unzip is required: without it mvnw takes the tar.gz distribution, which does not match the checksum.
# The jar is packaged without the tests: they run in continuous integration (.github/workflows/build.yml).
# The base images are pinned by the digests of their multi-platform indexes; Dependabot updates them
FROM eclipse-temurin:11-jdk@sha256:810d03ef723872693a38e5c1367cc88a0bee58edcf06a2ab868ccc048888db73 AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY mvnw pom.xml lombok.config ./
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dmaven.test.skip=true
# The Java runtime of the downloader: its module with the modules it requires, and the modules bound as services,
# which jlink does not add by itself: logback, the provider of SLF4J, and jdk.crypto.ec, the elliptic curves of the TLS
# of www.etsi.org. The empty /data is the mount point of the destination folder
RUN jlink --module-path "$(ls target/etsiorg-downloader-*.jar | grep -v -- -jar-with-dependencies):target/lib" \
          --add-modules com.github.sftwnd.etsiorg,ch.qos.logback.classic,jdk.crypto.ec \
          --strip-debug --no-header-files --no-man-pages --compress=2 \
          --output /runtime \
 && mkdir /data

# Runtime stage: the runtime on a minimal system with glibc and without a shell, run by its user nonroot (65532)
FROM gcr.io/distroless/base-debian12@sha256:fabbf1c0c357a3d42550111351daed089b20a2c954df13ee2fcff60602515e84
# The version is not a label: it is set in pom.xml only, and the downloader reports the version of its module
LABEL org.opencontainers.image.title="etsiorg-downloader" \
      org.opencontainers.image.description="Mirrors the document tree of ETSI, www.etsi.org/deliver, to a local folder" \
      org.opencontainers.image.source="https://github.com/sftwnd/etsi-org-downloader" \
      org.opencontainers.image.url="https://github.com/sftwnd/etsi-org-downloader" \
      org.opencontainers.image.licenses="BSD-3-Clause" \
      org.opencontainers.image.base.name="gcr.io/distroless/base-debian12"
COPY --from=build /runtime /opt/etsiorg
COPY src/main/resources/logback.xml /opt/etsiorg/logback.xml
COPY --from=build --chown=nonroot:nonroot /data /data
# The log of a container is read by docker logs and log collectors, not in a terminal: no colors (https://no-color.org).
# The mirror is written to the /data volume
ENV NO_COLOR=1 \
    ETSIORG_DEST=/data
USER nonroot
WORKDIR /data
# The heap is a share of the memory limit of the container, and an OutOfMemoryError ends the run with the status 3
ENTRYPOINT ["/opt/etsiorg/bin/java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
            "-Dlogback.configurationFile=/opt/etsiorg/logback.xml", "-m", "com.github.sftwnd.etsiorg"]

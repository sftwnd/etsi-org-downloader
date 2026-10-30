# syntax=docker/dockerfile:1

# Build stage: the Maven Wrapper downloads the Maven of the project and checks the SHA-256 of its zip distribution.
# unzip is required: without it mvnw takes the tar.gz distribution, which does not match the checksum.
# The jar is packaged without the tests: they run in continuous integration (.github/workflows/build.yml)
FROM eclipse-temurin:11-jdk AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY mvnw pom.xml lombok.config ./
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dmaven.test.skip=true

# Runtime stage: Java runtime only, the mirror is written by a non-root user to the /data volume
FROM eclipse-temurin:11-jre
RUN groupadd --system --gid 10001 etsiorg \
 && useradd --system --uid 10001 --gid etsiorg --home-dir /opt/etsiorg --no-create-home --shell /usr/sbin/nologin etsiorg \
 && mkdir -p /data \
 && chown etsiorg:etsiorg /data
WORKDIR /opt/etsiorg
# The log of a container is read by docker logs and log collectors, not in a terminal: no colors (https://no-color.org).
# The mirror is written to the /data volume
ENV NO_COLOR=1 \
    ETSIORG_DEST=/data
COPY --from=build /build/target/etsiorg-downloader-*-jar-with-dependencies.jar etsiorg-downloader.jar
COPY src/main/resources/logback.xml logback.xml
USER etsiorg
ENTRYPOINT ["java", "-Dlogback.configurationFile=/opt/etsiorg/logback.xml", "-jar", "/opt/etsiorg/etsiorg-downloader.jar"]

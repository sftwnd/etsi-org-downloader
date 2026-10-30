# syntax=docker/dockerfile:1

# Build stage: the official Maven image instead of a Maven archive downloaded without verification
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /build
COPY .settings.xml pom.xml lombok.config ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q -s .settings.xml package

# Runtime stage: Java runtime only, the mirror is written by a non-root user to the /data volume
FROM eclipse-temurin:11-jre
RUN groupadd --system --gid 10001 etsiorg \
 && useradd --system --uid 10001 --gid etsiorg --home-dir /opt/etsiorg --no-create-home --shell /usr/sbin/nologin etsiorg \
 && mkdir -p /data \
 && chown etsiorg:etsiorg /data
WORKDIR /opt/etsiorg
COPY --from=build /build/target/etsiorg-downloader-*-jar-with-dependencies.jar etsiorg-downloader.jar
COPY src/main/resources/logback.xml logback.xml
USER etsiorg
ENTRYPOINT ["java", "-Dlogback.configurationFile=/opt/etsiorg/logback.xml", "-Ddest=/data", "-jar", "/opt/etsiorg/etsiorg-downloader.jar"]

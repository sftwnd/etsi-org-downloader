#!/bin/bash
# Mirrors the ETSI documents tree. Arguments are passed to the JVM, e.g.: ./run.sh -Ddest=/data/etsi
set -euo pipefail
ETSI_ORG_VERSION=1.3-SNAPSHOT
ETSI_ORG_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ETSI_ORG_JAR="${ETSI_ORG_HOME}/target/etsiorg-downloader-${ETSI_ORG_VERSION}-jar-with-dependencies.jar"
LOGBACK_FILE="${ETSI_ORG_HOME}/src/main/resources/logback.xml"
if [ ! -f "${ETSI_ORG_JAR}" ]; then
  # .mvn/maven.config gives the project settings relative to the current folder, so Maven runs in the project folder
  (cd "${ETSI_ORG_HOME}" && ./mvnw -B clean package)
fi
exec java -Dlogback.configurationFile="${LOGBACK_FILE}" "$@" -jar "${ETSI_ORG_JAR}"

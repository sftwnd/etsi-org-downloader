#!/bin/bash
# Mirrors the ETSI documents tree. Arguments are passed to the JVM, e.g.: ./run.sh -Ddest=/data/etsi
set -euo pipefail
ETSI_ORG_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGBACK_FILE="${ETSI_ORG_HOME}/src/main/resources/logback.xml"
# The JDK the Maven Wrapper builds with: JAVA_HOME, otherwise java from PATH
JAVA_CMD="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

# The newest fat jar of the project, whatever its version, or nothing
fat_jar() {
  local jar newest=""
  for jar in "${ETSI_ORG_HOME}"/target/etsiorg-downloader-*-jar-with-dependencies.jar; do
    if [ -f "${jar}" ] && { [ -z "${newest}" ] || [ "${jar}" -nt "${newest}" ]; }; then
      newest="${jar}"
    fi
  done
  printf '%s' "${newest}"
}

ETSI_ORG_JAR="$(fat_jar)"
if [ -z "${ETSI_ORG_JAR}" ]; then
  # .mvn/maven.config gives the project settings relative to the current folder, so Maven runs in the project folder
  (cd "${ETSI_ORG_HOME}" && ./mvnw -B clean package)
  ETSI_ORG_JAR="$(fat_jar)"
fi
exec "${JAVA_CMD}" -Dlogback.configurationFile="${LOGBACK_FILE}" "$@" -jar "${ETSI_ORG_JAR}"

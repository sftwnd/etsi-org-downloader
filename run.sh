#!/bin/bash
# Mirrors the ETSI documents tree. Arguments are options of the JVM, e.g. ./run.sh -Ddest=/data/etsi, except -h and --help,
# which print the settings of the downloader; a setting may also be an environment variable: ETSIORG_DEST=/data/etsi ./run.sh
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

# A jar is outdated if a file it is built from has changed after it
outdated() {
  [ -n "$(find "${ETSI_ORG_HOME}/pom.xml" "${ETSI_ORG_HOME}/lombok.config" "${ETSI_ORG_HOME}/src/main" \
               "${ETSI_ORG_HOME}/src/assembly" -newer "$1" -print -quit)" ]
}

ETSI_ORG_JAR="$(fat_jar)"
if [ -z "${ETSI_ORG_JAR}" ] || outdated "${ETSI_ORG_JAR}"; then
  # .mvn/maven.config gives the project settings relative to the current folder, so Maven runs in the project folder
  (cd "${ETSI_ORG_HOME}" && ./mvnw -B clean package)
  ETSI_ORG_JAR="$(fat_jar)"
fi
JVM_OPTIONS=()
ARGUMENTS=()
for argument in "$@"; do
  case "${argument}" in
    -h|--help) ARGUMENTS+=("${argument}") ;;
    *) JVM_OPTIONS+=("${argument}") ;;
  esac
done
# ${array[@]+...} expands an empty array under set -u in bash 3.2 of macOS as well
# An OutOfMemoryError ends the run with the status 3 instead of leaving the JVM in an undefined state
exec "${JAVA_CMD}" -XX:+ExitOnOutOfMemoryError -Dlogback.configurationFile="${LOGBACK_FILE}" \
     ${JVM_OPTIONS[@]+"${JVM_OPTIONS[@]}"} -jar "${ETSI_ORG_JAR}" ${ARGUMENTS[@]+"${ARGUMENTS[@]}"}

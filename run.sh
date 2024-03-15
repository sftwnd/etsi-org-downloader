#!/bin/bash
ETSI_ORG_VERSION=1.2-SNAPSHOT
ETSI_ORG_JAR="etsiorg-downloader-${ETSI_ORG_VERSION}-jar-with-dependencies.jar"
LOGBACK_FILE="./src/main/resources/logback.xml"
if [ ! -f "target/${ETSI_ORG_JAR}" ]; then
  mvn -Dmaven.repo.local=~/.m2 clean package
fi
java -Dlogback.configurationFile=${LOGBACK_FILE} -jar target/${ETSI_ORG_JAR}

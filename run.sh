#!/bin/bash
if [ ! -f "target/ETSI.ORG-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
  mvn -Dmaven.repo.local=~/.m2 clean package
fi
java -Dlogback.configurationFile=./src/main/resources/logback.xml -jar target/ETSI.ORG-1.0-SNAPSHOT-jar-with-dependencies.jar

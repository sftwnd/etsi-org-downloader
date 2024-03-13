FROM openjdk:11-jdk-slim
USER root
ARG MAVEN_VERSION=3.9.6
ARG USER_HOME_DIR="/root"
ARG MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz
RUN apt-get update \
 && apt-get install -y tar curl \
 && mkdir -p /usr/share/maven /usr/share/maven/ref \
 && curl -fsSL -o /tmp/apache-maven.tar.gz ${MAVEN_URL} \
 && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
 && rm -f /tmp/apache-maven.tar.gz \
 && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn \
 && mkdir -p "/opt/share/etsiorg" \
 && apt-get clean all \
 && apt-get purge
ENV HOME="/root"
ENV M2_HOME /root/.m2
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG /root/.m2
WORKDIR "/opt/share/etsiorg"
ENTRYPOINT "/opt/share/etsiorg/run.sh"

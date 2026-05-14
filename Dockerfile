FROM jenkins/jenkins:lts-jdk17

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        subversion \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && . /etc/os-release \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        docker-buildx-plugin \
        docker-ce-cli \
        docker-compose-plugin \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ARG MAVEN_VERSION=3.9.9
RUN curl -fsSLo /tmp/apache-maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && tar -xzf /tmp/apache-maven.tar.gz -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven \
    && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn \
    && ln -s "${JAVA_HOME}/bin/java" /usr/local/bin/java \
    && rm /tmp/apache-maven.tar.gz
ENV MAVEN_HOME=/opt/maven
ENV PATH="${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${PATH}"

ARG KUBECTL_VERSION=v1.35.1
ARG TARGETARCH
RUN case "${TARGETARCH}" in \
        amd64|arm64) kubectl_arch="${TARGETARCH}" ;; \
        *) echo "Unsupported kubectl architecture: ${TARGETARCH}" >&2; exit 1 ;; \
    esac \
    && curl -fsSLo /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${kubectl_arch}/kubectl" \
    && chmod 0755 /usr/local/bin/kubectl

COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt

USER jenkins

RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

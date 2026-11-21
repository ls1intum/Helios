FROM gradle:9.9.2-jdk25-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Skip tests inside the image build — zonky/embedded-postgres integration tests
# (e.g. WorkflowRunOrphanSweepIntegrationTest) need a working Docker daemon, which
# isn't available inside buildkit. Tests are validated by the separate `server-tests`
# CI job that has socket access.
RUN gradle :application-server:build --no-daemon -x test

# The runtime uses Oracle JDK 25.0.5.1 until a Temurin 25 image of the same update release is published.
# The archive is pinned by version and verified against Oracle's published SHA-256 checksum.
FROM ubuntu:26.04 AS jdk
ARG TARGETARCH
ARG JDK_VERSION=25.0.5.1
ARG JDK_SHA256_AMD64=3d40d69461200594380ac84c17be6224539022028730cffe043ac70c41ffd1d5
ARG JDK_SHA256_ARM64=2c30d5fe69796452732d32e746c5f4b4031e4991e2be9a3313f76e51fe5196ee
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
RUN case "${TARGETARCH}" in \
        amd64) jdk_arch=x64; jdk_sha256="${JDK_SHA256_AMD64}" ;; \
        arm64) jdk_arch=aarch64; jdk_sha256="${JDK_SHA256_ARM64}" ;; \
        *) echo "Unsupported architecture: ${TARGETARCH}" >&2; exit 1 ;; \
    esac \
    && curl -fsSL -o /tmp/jdk.tar.gz "https://download.oracle.com/java/25/archive/jdk-${JDK_VERSION}_linux-${jdk_arch}_bin.tar.gz" \
    && echo "${jdk_sha256}  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/java/openjdk \
    && tar -xzf /tmp/jdk.tar.gz -C /opt/java/openjdk --strip-components=1 \
    && rm /tmp/jdk.tar.gz

FROM ubuntu:26.04

ENV LANG=C.UTF-8
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Certificates and fonts that the previous Temurin runtime image shipped.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates fontconfig \
    && rm -rf /var/lib/apt/lists/*

COPY --from=jdk /opt/java/openjdk /opt/java/openjdk

EXPOSE 8080

RUN mkdir /app

COPY --from=build /home/gradle/src/application-server/build/libs/*.jar /app/server.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/server.jar"]

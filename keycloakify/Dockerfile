# Maven and its JDK are build tools for packaging the Keycloak theme.
FROM maven:3.10.0-eclipse-temurin-25 AS maven

# Stage 1: Build the Keycloak theme
FROM node:26.13.0 AS build

# Set the working directory
WORKDIR /app

# Copy Maven and JDK 25 from the official Maven build image.
COPY --from=maven /usr/share/maven /usr/share/maven
COPY --from=maven /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/usr/share/maven/bin:${JAVA_HOME}/bin:${PATH}"

# Node 26 no longer bundles Corepack; match package.json "packageManager".
RUN npm install --global pnpm@12.16.1

# Copy manifest, lockfile, and pnpm settings (node-linker + allowed builds)
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy the entire project to the working directory
COPY . .

# Build the React app
RUN pnpm run build-keycloak-theme

# Keycloak runs on Oracle JDK 25.0.5.1 until a Temurin 25 image of the same update release is published.
# The archive is pinned by version and verified against Oracle's published SHA-256 checksum.
FROM ubuntu:24.04 AS java-runtime
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

# Stage 2: Build Keycloak image
# We use the specific version of Keycloak to avoid any changes in the realm.json file
# Do NOT update on renovatebot PRs
FROM quay.io/keycloak/keycloak:26.8.1

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Copy the built keycloakify theme to keycloak
COPY --from=build /app/dist_keycloak /opt/keycloak/providers/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]

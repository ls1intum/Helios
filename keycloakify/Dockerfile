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
RUN npm install --global pnpm@12.15.0

# Copy manifest, lockfile, and pnpm settings (node-linker + allowed builds)
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy the entire project to the working directory
COPY . .

# Build the React app
RUN pnpm run build-keycloak-theme

# Copy the Temurin 25 JRE into the vendor Keycloak runtime image.
FROM eclipse-temurin:25.0.4.1_1-jre-ubi10-minimal AS java-runtime

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

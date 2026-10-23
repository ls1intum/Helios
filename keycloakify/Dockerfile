# Maven and its JDK are build tools for packaging the Keycloak theme.
FROM maven:3.9.16-eclipse-temurin-17 AS maven

# Stage 1: Build the Keycloak theme
FROM node:24.22.1 AS build

# Set the working directory
WORKDIR /app

# Use the current Maven 3.9 release instead of Debian Bookworm's older package.
COPY --from=maven /usr/share/maven /usr/share/maven
COPY --from=maven /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/usr/share/maven/bin:${JAVA_HOME}/bin:${PATH}"

# Enable pnpm via corepack (version pinned by package.json "packageManager")
RUN corepack enable

# Copy manifest, lockfile, and pnpm settings (node-linker + allowed builds)
COPY package.json pnpm-lock.yaml .npmrc pnpm-workspace.yaml ./

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy the entire project to the working directory
COPY . .

# Build the React app
RUN pnpm run build-keycloak-theme

# Stage 2: Build Keycloak image
# We use the specific version of Keycloak to avoid any changes in the realm.json file
# Do NOT update on renovatebot PRs
FROM quay.io/keycloak/keycloak:26.8.1

# Copy the built keycloakify theme to keycloak
COPY --from=build /app/dist_keycloak /opt/keycloak/providers/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]

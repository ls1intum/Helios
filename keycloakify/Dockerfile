# Stage 1: Build Angular app
FROM node:24.18.0 AS build

# Set the working directory
WORKDIR /app

# Install maven for the build step
RUN apt-get update && apt-get install maven=3.8.7-1 -y

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
FROM quay.io/keycloak/keycloak:26.6.4

# Copy the built keycloakify theme to keycloak
COPY --from=build /app/dist_keycloak /opt/keycloak/providers/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]

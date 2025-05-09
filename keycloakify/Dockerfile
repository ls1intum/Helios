# Stage 1: Build Angular app
FROM node:22 AS build

# Set the working directory
WORKDIR /app

# Install maven for the build step
RUN apt-get update && apt-get install maven=3.8.7-1 -y

# Copy package.json and yarn.lock
COPY package.json yarn.lock ./

# Install dependencies
RUN yarn install --network-timeout 1000000

# Copy the entire project to the working directory
COPY . .

# Build the React app
RUN yarn run build-keycloak-theme

# Stage 2: Build Keycloak image
# We use the specific version of Keycloak to avoid any changes in the realm.json file
# Do NOT update on renovatebot PRs
FROM quay.io/keycloak/keycloak:26.2.4

# Copy the built keycloakify theme to keycloak
COPY --from=build /app/dist_keycloak /opt/keycloak/providers/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
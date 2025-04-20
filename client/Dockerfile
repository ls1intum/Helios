# Stage 1: Build Angular app
FROM node:22 AS build

# Set the working directory
WORKDIR /app

# Copy package.json and yarn.lock
COPY package.json yarn.lock ./

# Install dependencies
RUN yarn install --network-timeout 1000000

# Copy the entire project to the working directory
COPY . .

# Set the build configuration based on the environment variables
ARG BUILD_CONFIG=production
ARG DEPLOYED_VERSION=''
ARG DEPLOYED_COMMIT_SHA=''

# Set version in environment version file
RUN echo "export const version = { deployed_version: "$DEPLOYED_VERSION", deployed_commit_sha: "$DEPLOYED_COMMIT_SHA" };" > /app/src/environments/version.ts

# Build the Angular app using the specified configuration
RUN yarn run build --configuration $BUILD_CONFIG

# Stage 2: Serve Angular app using Nginx
FROM nginx:1.27.4-alpine

# Remove the default Nginx configuration file
RUN rm /etc/nginx/conf.d/default.conf

# Copy your custom Nginx configuration file
COPY nginx.conf /etc/nginx/conf.d/

# Copy the built Angular app from Stage 1
COPY --from=build /app/dist/client/browser /usr/share/nginx/html

# Expose the HTTP port
EXPOSE 80

# Start Nginx server
CMD ["nginx", "-g", "daemon off;"]

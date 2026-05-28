FROM gradle:9.5.1-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Skip tests inside the image build — zonky/embedded-postgres integration tests
# (e.g. WorkflowRunOrphanSweepIntegrationTest) need a working Docker daemon, which
# isn't available inside buildkit. Tests are validated by the separate `server-tests`
# CI job that has socket access.
RUN gradle :application-server:build --no-daemon -x test

FROM eclipse-temurin:25

EXPOSE 8080

RUN mkdir /app

COPY --from=build /home/gradle/src/application-server/build/libs/*.jar /app/server.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/server.jar"]

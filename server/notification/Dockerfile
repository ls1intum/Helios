FROM gradle:9.5.1-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Skip tests inside the image build — consistent with application-server (tests
# are validated by the separate CI job, not the Docker build context).
RUN gradle :notification:build --no-daemon -x test

FROM eclipse-temurin:25

EXPOSE 8080

RUN mkdir /app

COPY --from=build /home/gradle/src/notification/build/libs/*.jar /app/server.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/server.jar"]

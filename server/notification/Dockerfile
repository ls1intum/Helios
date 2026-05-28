FROM gradle:9.5.1-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :notification:build --no-daemon

FROM eclipse-temurin:25

EXPOSE 8080

RUN mkdir /app

COPY --from=build /home/gradle/src/notification/build/libs/*.jar /app/server.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/server.jar"]

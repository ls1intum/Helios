FROM gradle:8.12.1-jdk21-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :application-server:build --no-daemon

FROM eclipse-temurin:21

EXPOSE 8080

RUN mkdir /app

RUN wget https://repo1.maven.org/maven2/org/aspectj/aspectjweaver/1.9.22.1/aspectjweaver-1.9.22.1.jar -O /app/aspectjweaver.jar
RUN wget https://repo1.maven.org/maven2/org/springframework/spring-instrument/6.2.1/spring-instrument-6.2.1.jar -O /app/spring-instrument.jar

COPY --from=build /home/gradle/src/application-server/build/libs/*.jar /app/server.jar

ENTRYPOINT ["java", "-javaagent:/app/spring-instrument.jar", "-javaagent:/app/aspectjweaver.jar", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/server.jar"]
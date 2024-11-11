# Helios Application Server

Helios Application Server is a [Spring Boot application](https://spring.io/projects/spring-boot). It provides a REST API for the Helios client to interact with the database and other services.

## Prerequisites

- [Java 22](https://www.oracle.com/java/technologies/downloads/) (or higher)
- [Docker](https://docs.docker.com/engine/install/) (for automated database setup) or [Postgres 16](https://www.postgresql.org/download/)

## Getting Started

**1. Clone the Repository**

Clone the Helios repository to your local machine.

```bash
$ git clone https://github.com/ls1intum/Helios.git
$ cd helios/server/application-server
```

**2. Build the Project**

Build the project using Gradle.

```bash
$ ./gradlew build
```

**3. Setup configuration and environment**

Copy the file `.env.example` to `.env` and adjust the values to your needs. It is automatically set up to work with the Docker Compose setup.

```bash
$ cp .env.example .env
```

You can use the provided Docker Compose setup to start a local Postgres server. This is the easiest way to get started.

```bash
$ docker compose up -d
```

**4. Run the Application**

```bash
$ ./gradlew bootRun
```

The application will be accessible at [http://localhost:8080/status/health](http://localhost:8080/status/health)

## Development

### Run tests

```bash
$ ./gradlew test
```

### Generate OpenAPI YAML

```bash
$ ./gradlew generateOpenApiDocs
```

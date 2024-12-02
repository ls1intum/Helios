# Helios Application Server

Helios Application Server is a [Spring Boot application](https://spring.io/projects/spring-boot). It provides a REST API for the Helios client to interact with the database and other services.

## Prerequisites

- [Java 21](https://www.oracle.com/java/technologies/downloads/) (or higher)
- [Postgres 16](https://www.postgresql.org/download/) (is automatically started with the provided Docker Compose setup)

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

Copy the file `.env.example` to `.env` and adjust the values to your needs. It is set up to work with the Docker Compose setup for the database and NATS server (see [here](../../README.md#development-setup)).

```bash
$ cp .env.example .env
```

**4. Run the Application in Development Mode**

```bash
$ ./gradlew bootRunDev
```

The application will be accessible at [http://localhost:8080/status/health](http://localhost:8080/status/health)

## Development

* For swagger-ui, use the following URL: http://localhost:8080/swagger-ui/index.html

### Run tests

```bash
$ ./gradlew test
```

### Generate OpenAPI YAML

```bash
$ ./gradlew generateOpenApiDocs
```

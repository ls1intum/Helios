# Helios Status Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/de.tum.cit.aet/helios-status-spring-starter.svg)](https://search.maven.org/artifact/de.tum.cit.aet/helios-status-spring-starter) [![Javadocs](https://javadoc.io/badge2/de.tum.cit.aet/helios-status-spring-starter/javadoc.svg)](https://javadoc.io/doc/de.tum.cit.aet/helios-status-spring-starter) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/ls1intum/Helios/blob/staging/LICENSE)

A plug‑and‑play **Spring Boot 3** starter that transparently reports your service’s **lifecycle** and **heartbeat**
events to a Helios instance.

Just add the dependency, add three YAML lines, and you are done.


> **Stay on the latest release:**
> The Helios API evolves and occasionally receives security hardening.
> Always consume the **most recent starter version**—older majors may stop
> working once your Helios instance is upgraded.

<!-- toc -->

- [1. Add the dependency](#1-add-the-dependency)
- [2. Configure your service (`application.yml`)](#2-configure-your-service-applicationyml)
- [3. Generate a Helios secret key](#3-generate-a-helios-secret-key)
- [4. Enable push updates in Helios](#4-enable-push-updates-in-helios)
- [Logging](#logging)
- [What events are sent?](#what-events-are-sent)
- [Blocking vs. Non-blocking API](#blocking-vs-non-blocking-api)
- [Manual status updates (optional)](#manual-status-updates-optional)
- [Source & Releases](#source--releases)
- [License](#license)

<!-- tocstop -->

<!-- How to auto-generate the table of contents? -->
<!-- npm install -g markdown-toc -->
<!-- markdown-toc -i PACKAGE_README.md -->

---

### 1. Add the dependency

Official GA releases are published to Maven Central; snapshots (`-SNAPSHOT`) are available on GitHub Packages and may change at any time. Make sure you have Maven Central in your repositories.

<details>
<summary>Gradle – Groovy DSL</summary>

```gradle
dependencies {
    implementation "de.tum.cit.aet:helios-status-spring-starter:<LATEST_VERSION>"
}
```

</details>

<details>
<summary>Maven – pom.xml</summary>

```xml

<dependencies>
    <dependency>
        <groupId>de.tum.cit.aet</groupId>
        <artifactId>helios-status-spring-starter</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
</dependencies>
```

</details>

> **Tip:** use Dependabot / Renovate so your project is bumped
> automatically whenever a new version is published.

### 2. Configure your service (`application.yml`)

```yaml
helios:
  status:
    enabled: true
    environment-name: production # must match the GitHub Environment name in your GitHub repo settings
    endpoints:
      - url: https://helios.aet.cit.tum.de/ # prod Helios
        secret-key: ${HELIOS_SECRET_KEY} # Secret key generated in production Helios
```

| key                    | required? | notes                                                                                            |
|------------------------|-----------|--------------------------------------------------------------------------------------------------|
| environment-name       | ✔         | Same name you use under Repo ▸ Settings ▸ Environments.                                          |
| endpoints[].url        | ✔         | Helios instance URL (prod or staging `https://helios-staging.aet.cit.tum.de/`).                  |
| endpoints[].secret-key | ✔         | Secret key generated in Helios. Generate in Helios ▸ Repository settings ▸ Generate secret key.. |
| heartbeat-interval     | ✘         | Default is 30s – no need to set.                                                                 |

### 3. Generate a Helios secret key

In your Helios instance (prod, staging, etc.):

1. Select your repository and navigate to **Repository Settings**.
2. Under **Shared Secret** click **Generate**.
3. Save the key in an environment variable:
4. Reference that variable in your **application.yml**:

> **Note:** the generated secret is **repository-scoped**, not per-environment. Once created, the same key applies to all environments under that repository.

### 4. Enable push updates in Helios

In your Helios instance (prod, staging, etc.):

1. Select your repository and navigate to **Environments**.
2. Click **Edit** on your environment.
3. In **Status Check Configuration** select **Push Update**.
4. Save.

---

### Logging

By default, Helios uses SLF4J with these log levels baked into our starter’s `logback-spring.xml`:
```xml
<!-- Only real errors from the Helios client show by default -->
<logger name="de.tum.cit.aet.helios" level="ERROR"/>

<!-- INFO so that on application startup you’ll see whether Helios push-updates are enabled or disabled -->
<logger name="de.tum.cit.aet.helios.autoconfig.HeliosStatusAutoConfiguration" level="INFO"/>
```

If you supply your own `logback-spring.xml` (or `logback.xml`), your settings simply override these.

Alternatively, without touching XML, you can change Helios’s log level in `application.yml`:

```yaml
logging:
  level:
    de.tum.cit.aet.helios: DEBUG # overrides the ERROR default
    de.tum.cit.aet.helios.autoconfig.HeliosStatusAutoConfiguration: INFO
```


### What events are sent?

| Application event         | Helios state          |
|---------------------------|-----------------------|
| `ApplicationStartedEvent` | `STARTING_UP`         |
| `ApplicationReadyEvent`   | `RUNNING`             |
| Every 30 s                | `RUNNING` (heartbeat) |
| `ContextClosedEvent`      | `SHUTTING_DOWN`       |
| `ApplicationFailedEvent`  | `FAILED`              |

### Blocking vs. Non-blocking API

By default, most Helios calls are *fire-and-forget* (asynchronous) so they never block your application:

```java
helios.pushStartingUp(); // async
helios.pushRunning(); // async
helios.pushStatusUpdate(...); // async for most states
```

However, certain “must-deliver” states are sent synchronously (blocking) to ensure they reach Helios before your app
exits:

- `LifecycleState.DB_MIGRATION_FAILED`
- `LifecycleState.FAILED`
- `LifecycleState.SHUTTING_DOWN`

```java
helios.pushDbMigrationFailed(); // sync
helios.pushFailed(); // sync
helios.pushShuttingDown(); // sync
```

> Note: the starter does not automatically send DB-migration lifecycle events for you—you’ll need to hook into your own
> migration logic and call.

### Manual status updates (optional)

If you prefer to call Helios lifecycle events yourself, just inject the HeliosClient bean.

```java

@Autowired
private HeliosClient helios;

public void migrateDatabase() {
  helios.pushDbMigrationStarted(); // async
  try {
    // … your migration logic …
    helios.pushDbMigrationFinished(); // async
  } catch (Exception e) {
    helios.pushDbMigrationFailed(); // synchronous
    throw e;
  }
}
```

You can also push any arbitrary state:

```java
helios.pushStatusUpdate(
    LifecycleState.DEGRADED,
    Map.of("reason", "downstream timeout","timeoutMs",5000)
);
```

---

### Source & Releases

- **Source (SNAPSHOTs / development)**  
  Browse the full Helios monorepo on GitHub—our Spring Boot starter lives under `server/helios-status-spring-starter` on
  the `staging` branch:  
  <https://github.com/ls1intum/Helios/tree/staging/server/helios-status-spring-starter>

- **Releases (GA)**  
  Production-ready versions are published to Maven Central:  
  <https://central.sonatype.com/artifact/de.tum.cit.aet/helios-status-spring-starter>

This way:

- Developers can grab SNAPSHOTs directly from GitHub (GitHub Packages registry).
- Consumers can use the stable, GA artifacts on Maven Central.

### License

This project is licensed under the MIT License – see
the [LICENSE](https://github.com/ls1intum/Helios/blob/staging/LICENSE) file in the root of the Helios monorepo for full
details.

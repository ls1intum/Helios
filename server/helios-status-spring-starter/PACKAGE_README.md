# Helios Status Spring Boot Starter

A plug‑and‑play **Spring Boot 3** starter that transparently reports your service’s **lifecycle** and **heartbeat** events to a Helios instance.

Just add the dependency, add three YAML lines, and you are done.


> **Stay on the latest release:**
> The Helios API evolves and occasionally receives security hardening.
Always consume the **most recent starter version**—older majors may stop
working once your Helios instance is upgraded.

---

### 1. Add the dependency

The package is **public**, so no tokens needed.

<details>
<summary>Gradle – Groovy DSL</summary>

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.github.com/ls1intum/Helios") }
}

dependencies {
    implementation "de.tum.cit.aet.helios:helios-status-spring-boot-starter:<LATEST_VERSION>"
}
```
</details>

<details>
<summary>Maven – pom.xml</summary>

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/ls1intum/Helios</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>de.tum.cit.aet.helios</groupId>
        <artifactId>helios-status-spring-boot-starter</artifactId>
        <version>LATEST_VERSION</version>
    </dependency>
</dependencies>
```
</details>

> **Tip:** use Dependabot / Renovate so your project is bumped
automatically whenever a new version is published.

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

### 3. Enable push updates in Helios
1. Go to the Helios instance (prod, staging, etc.).
2. Select your repository and go to the Environments page.
3. Click Edit on your environment.
4. In Status Check Configuration select Push update.
5. Save.

### Logging

The starter uses the `org.slf4j` logger. By default, the log level is set to `ERROR` (only errors are logged). Additionally, the entry point `de.tum.cit.aet.helios.autoconfig.HeliosStatusAutoConfiguration` is set to `INFO` by default, as it only logs whether push status updates are enabled. You can adjust these settings in your `application.yml`:

```yaml
logging:
  level:
    de.tum.cit.aet.helios: DEBUG # or INFO
```


### What events are sent?
| Application event                          | Helios state                                               |
|--------------------------------------------|------------------------------------------------------------|
| `ApplicationStartedEvent`                  | `STARTING_UP`                                              |
| `ApplicationReadyEvent`                    | `RUNNING`                                                  |
| Every 30 s                                 | `RUNNING` (heartbeat)                                      |
| `ContextClosedEvent`                       | `SHUTTING_DOWN`                                            |
| `ApplicationFailedEvent`                   | `FAILED`                                                   |

- All HTTP calls are non‑blocking and use a bounded single‑thread pool.
- DB migration related status updates will be implemented in the future.

### Manual status updates (optional)
```java
@Autowired HeliosClient helios;

// Push a status update
helios.pushStatusUpdate(LifecycleState.DB_MIGRATION_STARTED);

// Push status update with additional data
helios.pushStatusUpdate(LifecycleState.DB_MIGRATION_FINISHED, new HashMap<>() {{
    put("migrationId", "123456");
  }}
);
```

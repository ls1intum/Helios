# Helios Status Spring Boot Starter

Drop-in Spring Boot 3 starter that pushes **lifecycle & heartbeat events** to a
Helios instance (prod or staging) with zero boilerplate.

---

## 1. Add the dependency

The package is **public**, so no credentials are required.

<details>
<summary>Gradle – Groovy DSL</summary>

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.github.com/ls1intum/Helios") }
}

dependencies {
    implementation "de.tum.cit.aet.helios:helios-status-spring-boot-starter:0.1.1"
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
        <version>0.1.1</version>
    </dependency>
</dependencies>
```
</details>

## 2. Configure your service (`application.yml`)
```yaml
helios:
  status:
    enabled: true
    environment-name: production # must match the GitHub Environment name in your GitHub repo settings
    endpoints:
      - url: https://helios.aet.cit.tum.de/ # prod Helios
        secret-key: ${HELIOS_SECRET_KEY} # Secret key generated in Helios
```

| key                    | required? | notes                                                                                            |
|------------------------|-----------|--------------------------------------------------------------------------------------------------|
| environment-name       | ✔         | Same name you use under Repo ▸ Settings ▸ Environments.                                          |
| endpoints[].url        | ✔         | Helios instance URL (prod or staging `https://helios-staging.aet.cit.tum.de/`).                  |
| endpoints[].secret-key | ✔         | Secret key generated in Helios. Generate in Helios ▸ Repository settings ▸ Generate secret key.. |
| heartbeat-interval     | ✘         | Default is 30 s – no need to set.                                                                |

## 3. Enable push updates in Helios
1. Go to your Helios instance (prod or staging).
2. Select your repository and go to the Environments page.
3. Click Edit on your environment.
4. In Status Check Configuration select Push update.
5. Save.

### What events are sent?
| Application event                          | Helios state                                             |
|--------------------------------------------|----------------------------------------------------------|
| `ApplicationStartedEvent`                  | **STARTING_UP**                                          |
| Flyway migration start / success / failure | **MIGRATING_DB / MIGRATION_FINISHED / MIGRATION_FAILED** |
| `ApplicationReadyEvent`                    | `RUNNING`                                                |
| Every 30 s                                 | `RUNNING` (heartbeat)                                    |
| `ContextClosedEvent`                       | `SHUTTING_DOWN`                                          |
| `ApplicationFailedEvent`                   | `FAILED`                                                 |

- All requests are non-blocking (`WebClient`).

### Manual status override (optional)
```java
@Autowired HeliosClient helios;

helios.push(LifecycleState.DEGRADED).subscribe();
```

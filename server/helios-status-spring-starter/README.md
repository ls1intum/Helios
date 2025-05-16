## 🚀 What is a Spring Boot Starter?

A **Spring Boot starter** is a pre-configured library that makes it easy to integrate specific functionality into a Spring Boot application.

This library, **helios-status-spring-starter**, is a lightweight starter that adds automatic lifecycle status reporting to your app (e.g. `RUNNING`, `FAILED`, `SHUTTING_DOWN`) via HTTP push.

It:
- Automatically registers beans via `@AutoConfiguration`
- Supports `application.yml` configuration
- Requires no manual wiring
- Does **nothing** unless `helios.status.enabled=true` is set

To use it, please read the [**PACKAGE_README**](./PACKAGE_README.md) of the main project.

## 📦 Publishing the starter

This section explains how to publish the `helios-status-spring-starter` library for testing and production use.


> A **snapshot** version in Maven is one that has not been released.
> 
> The idea is that before a `1.0` release (or any other release) is done, there exists a `1.0-SNAPSHOT`. That version is what might become `1.0`. It's basically "`1.0` under development". This might be close to a real `1.0` release, or pretty far (right after the `0.9` release, for example).
> 
> The difference between a “real” version and a snapshot version is that snapshots might get updates. That means that downloading `1.0-SNAPSHOT` today might give a different file than downloading it yesterday or tomorrow.
> 
> Usually, snapshot dependencies should only exist during development and no released version (i.e. no non-snapshot) should have a dependency on a snapshot version.
> 
> Reference: [stackoverflow - What exactly is a Maven Snapshot and why do we need it?](https://stackoverflow.com/a/5901460)


- ⚠️ Once a version is published, it is **immutable** (Maven Central). You **can not** overwrite or delete it.
- Only the version numbers end with `-SNAPSHOT` can be overwritten.
- `-SNAPSHOT` versions are cleaned up automatically after a period of time (currently 90 days).
- After releasing -SNAPSHOT version, access them in https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/
- To consume -SNAPSHOT versions
```
repositories {
  maven {
    name = 'Central Portal Snapshots'
    url = 'https://central.sonatype.com/repository/maven-snapshots/'

    // Only search this repository for the specific dependency
    content {
      includeModule("<the snapshot's groupId>", "<the snapshot's artifactId>")
    }
  }
  mavenCentral()
}
```
- After deployment to staging, finalize deployment from https://central.sonatype.com/publishing/deployments


### 1. Publish Locally for Testing (Maven Local)

Use this when testing the library in another local project.

```bash
# from the server/ directory

# Option 1: Using environment variable
# choose any version you like
export VERSION=0.1.1           
./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publishToMavenLocal --no-daemon --warn --stacktrace

# Option 2: Using CLI parameter
./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publishToMavenLocal -Pversion=0.1.1 --no-daemon --warn --stacktrace
```

Artifacts are published to:
```
~/.m2/repository/de/tum/cit/aet/helios/helios-status-spring-starter/0.1.1/
```

In your test app’s `build.gradle`:
```
repositories { mavenLocal() }

dependencies {
    implementation "de.tum.cit.aet.helios:helios-status-spring-starter:0.1.1"
}
```

To pick up the latest build:
```bash
./gradlew --refresh-dependencies bootRun --no-daemon
```

### 2. Publish to GitHub Packages (Manual)

```bash
# Inside the server/ directory
export VERSION=0.1.1
export GITHUB_TOKEN=<your-personal-access-token> # write:packages

./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publish --no-daemon --warn --stacktrace
```

### 3. Publish via GitHub Actions (Workflow Dispatch)

- Go to the **Actions** tab in GitHub.
- Select the **Publish Helios starter** workflow.
- Click **Run workflow**, enter a version like **0.1.1**, and click **Run**.


### 4. Version precedence rules

| Priority     | How to set                     | Example                |
|--------------|--------------------------------|------------------------|
| 1  (highest) | `-Pversion` CLI flag           | `-Pversion=0.1.1`      |
| 2            | Environment variable `VERSION` | `export VERSION=0.1.1` |
| 3            | Default in `settings.gradle`   | `0.0.1-SNAPSHOT`       |

All subprojects automatically inherit the selected version.



## 📦 Publishing the starter

This section explains how to publish the `helios-status-spring-starter` library for testing and production use.

### 1. Publish Locally for Testing (Maven Local)

Use this when testing the library in another local project.

```bash
# from the server/ directory

# Option 1: Using environment variable
# choose any version you like
export VERSION=0.1.1           
./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publishToMavenLocal --no-daemon

# Option 2: Using CLI parameter
./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publishToMavenLocal -Pversion=0.1.1 --no-daemon
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

./gradlew :helios-status-spring-starter:clean :helios-status-spring-starter:publish --no-daemon
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



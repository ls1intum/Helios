## 📦 Publishing the starter

### 1. Local test publish (Maven local)

```bash
# from the server/ directory
# choose any version you like
export VERSION=0.1.0-SNAPSHOT           
./gradlew :helios-status-spring-starter:publishToMavenLocal --no-daemon
# or use -Pversion=0.1.0-SNAPSHOT
../gradlew :helios-status-spring-starter:publishToMavenLocal -Pversion=0.1.0-SNAPSHOT --no-daemon
```

Artifacts land in:
```bash
# ~/.m2/repository/de/tum/cit/aet/helios/helios-status-spring-starter/0.1.0-SNAPSHOT/
```

Add the dependency in another project:
```
repositories { mavenLocal() }

dependencies {
    implementation "de.tum.cit.aet.helios:helios-status-spring-starter:0.1.0-SNAPSHOT"
}
```

### 2. Manual publish to GitHub Packages from your local

```bash
# from the server/ directory
# ➊ set version and GitHub credentials
export VERSION=0.1.0
export GITHUB_TOKEN=<PAT with write:packages>
# ➋ run the publish task
./gradlew :helios-status-spring-starter:publish --no-daemon
```

### 3. Publish via GitHub Actions (workflow dispatch)

- In the Actions tab, choose “Publish Helios starter” → Run workflow, type the desired version (e.g. 1.0.0), click Run.

### 4. Version precedence rules

| Priority     | How to set                     | Example                |
|--------------|--------------------------------|------------------------|
| 1  (highest) | `-Pversion` CLI flag           | `-Pversion=0.1.0`      |
| 2            | Environment variable `VERSION` | `export VERSION=0.1.0` |
| 3            | Default in `settings.gradle`   | `0.0.1-SNAPSHOT`       |

Each sub-module picks up the same version automatically.


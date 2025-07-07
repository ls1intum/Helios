## 🚀 What is a Spring Boot Starter?

A **Spring Boot starter** is a pre-configured library that makes it easy to integrate specific functionality into a Spring Boot application.

This library, **helios-status-spring-starter**, is a lightweight starter that adds automatic lifecycle status reporting to your app (e.g. `RUNNING`, `FAILED`, `SHUTTING_DOWN`) via HTTP push.

It:
- Automatically registers beans via `@AutoConfiguration`
- Supports `application.yml` configuration
- Requires no manual wiring
- Does **nothing** unless you enable it with
```yaml
helios:
  status:
    enabled: true
```

To use it, please read the [**PACKAGE_README**](./PACKAGE_README.md) of the main project.

## 📦 Publishing the starter

This section explains how to publish the `helios-status-spring-starter` library for testing and production use.

Starting with this release the project uses Maven (`pom.xml`) instead of Gradle, for better plugin support when publishing to Maven Central. The standard deploy command is:

```
mvn -B deploy -Pcentral-deploy -DskipTests=true
```

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
# From the project root:
mvn clean install -DskipTests=true
```

Artifacts are published to:
```
~/.m2/repository/de/tum/cit/aet/helios/helios-status-spring-starter/
```

In your test app’s `build.gradle`:
```
repositories { mavenLocal() }

dependencies {
    implementation "de.tum.cit.aet.helios:helios-status-spring-starter:<VERSION>"
}
```

To pick up the latest build:
```bash
./gradlew --refresh-dependencies bootRun --no-daemon
```

### 2. Publish to GitHub Packages (Manual)

For manual GitHub Package publishing:

1. Ensure your `~/.m2/settings.xml` contains a server entry for GitHub (see below).
2. Generate a GitHub PAT with at least the `write:packages` scope.
3. Run:

```bash
mvn clean deploy -DskipTests=true
```

### 3. Publish via GitHub Actions (Workflow Dispatch)

- Go to the **Actions** tab in GitHub.
- Select the **Publish Helios starter** workflow.
- Click **Run workflow**, enter a version like **0.1.1**, and click **Run**.

> This workflow uses the same `mvn deploy -Pcentral-deploy` command under the hood, but is triggered from GitHub and picks up GITHUB_TOKEN automatically.


### 4. Publish to Maven Central

To push to Maven Central you must:

- Use the `central-deploy` profile:
```
mvn -B deploy -Pcentral-deploy -DskipTests=true
```
- Have your `~/.m2/settings.xml` configured like this:
```
<settings>
  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>${PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <servers>
    <server>
      <id>github</id>
      <username>${GITHUB_ACTOR}</username>
      <password>${GITHUB_TOKEN}</password>
    </server>
    <server>
      <id>central</id>
      <username>${CENTRAL_USERNAME}</username>
      <password>${CENTRAL_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

- Set the following environment variables:
  - `GITHUB_ACTOR` - GitHub username
  - `GITHUB_TOKEN` - GitHub PAT with `write:packages` scope
  - `CENTRAL_USERNAME` - Sonatype username (follow the guide [Sonatype - Generate portal token](https://central.sonatype.org/publish/generate-portal-token/))
  - `CENTRAL_PASSWORD` - Sonatype password (follow the guide [Sonatype - Generate portal token](https://central.sonatype.org/publish/generate-portal-token/))
  - `PASSPHRASE` - GPG passphrase
  - `gpg` - is the cli tool that is installed on your system. It is used to sign the artifacts before uploading them to the repository.


For signing artifacts, you need to have a GPG key pair. You can follow this guide to create one: [Sonatype - GPG](https://central.sonatype.org/publish/requirements/gpg/).


> **Manual Signing & Validation**
> Each Central release requires a validator to sign the package. We provide a GitHub Actions template, but you still must trigger deployments manually (or via the dispatch workflow) so a human can review/sign.

### 🔖 Tagging policy & `CHANGELOG.md`

* **Every public release is tagged** `helios-starter-v<MAJOR.MINOR.PATCH>`  
  (e.g. `helios-starter-v1.1.0`).
* Copy and populate the **Unreleased** block in `CHANGELOG.md` to a new dated section.
* Tags can be pushed **only** by members of the *Helios Maintainers* team; the
  repository has a tag-protection rule to enforce this.
* Always tag the merge commit that bumps `pom.xml` and `CHANGELOG.md`.

<details>
<summary>✏️ How to create the tag after the PR is merged & the deploy has run</summary>

```bash
# 1 – Switch to the merge commit
git checkout <SHA>
git pull

# 2 – Confirm you are on the correct commit
git log -1 # should show the version-bump commit

# 3 – Create an *annotated* tag with a clear message
git tag -a helios-starter-v1.1.0 \
         -m "Helios starter 1.1.0 – RestClient replaces OkHttp"

# 4 – Push just the tag
git push origin helios-starter-v1.1.0
```
</details>

If you run into any issues—or if you’re not yet added to the namespace—please reach out to [@egekocabas](https://github.com/egekocabas) (or [@krusche](https://github.com/krusche)) for assistance.

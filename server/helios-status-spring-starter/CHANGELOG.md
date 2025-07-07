# Changelog – Helios Status Spring Boot Starter
All notable changes to this module are documented here.  
The format follows **[Keep a Changelog 1.1.0]** and the project adheres to **[Semantic Versioning 2.0.0]**.

<!--
  For each pull-request touching the starter, update the *Unreleased* section.
  When cutting an official release:
    1. Move changes from *Unreleased* to a new, dated version heading.
    2. Bump the version in pom.xml and commit both files together.
    3. Tag the commit:  git tag -a helios-starter-vX.Y.Z -m "Helios starter X.Y.Z"
-->

## [Unreleased]
### Added
- _(nothing yet)_
### Changed
- _(nothing yet)_
### Deprecated
- _(nothing yet)_
### Removed
- _(nothing yet)_
### Fixed
- _(nothing yet)_
### Security
- _(nothing yet)_
### Migration notes
- _(nothing yet)_

---

## [1.1.0] – 2025-07-07
### Added
- **Dependency:** `org.springframework:spring-web` (pulled in transitively by many apps, but now
  declared explicitly so the starter compiles standalone).
- **HTTP client:** now uses Spring Framework’s **`RestClient`** which Boot auto-configures and
  exposes via `RestClient.Builder`.

### Changed
- **Async delivery:** OkHttp’s dispatcher was replaced with  
  `CompletableFuture.runAsync(…, singleThreadExecutor)` + `orTimeout(10 s)`.  
  Behaviour is identical: one daemon thread, bounded queue = 10, oldest entry dropped on overflow.
- **Timeouts:** kept at 5 s connect / 5 s read; overall call timeout enforced at 10 s via
  `CompletableFuture.orTimeout`.

### Removed
- **OkHttp 4.x** runtime dependency & its transitive `okio`/`okhttp-logging` jars.
- All OkHttp-specific code paths (`Request`, `Call`, `Dispatcher`, `MediaType JSON`, etc.).

### Migration notes
1. No code changes for consumers; the public API of `HeliosClient` is unchanged.
2. If you had custom `okhttp3` log-level overrides in `application.yml` or `logback.xml`
   you can delete them—they no longer have any effect.

---

## [1.0.0] – 2024-12-04
### Added
- First public release extracted from the Helios monorepo.  
  Features:
    - Auto-configuration (`@AutoConfiguration`) + YAML support.
    - OkHttp-based async / sync status pushes.
    - Heart-beat every 30 s (configurable).
    - Package README and comprehensive publishing instructions.

[Unreleased]:   https://github.com/ls1intum/Helios/compare/helios-starter-v1.1.0...HEAD
[1.1.0]:        https://github.com/ls1intum/Helios/compare/helios-starter-v1.0.0...helios-starter-v1.1.0
[1.0.0]:        https://github.com/ls1intum/Helios/tree/helios-starter-v1.0.0
[Keep a Changelog 1.1.0]: https://keepachangelog.com/en/1.1.0/
[Semantic Versioning 2.0.0]: https://semver.org/spec/v2.0.0.html

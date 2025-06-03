============
Architecture
============

.. contents:: Content of this document
    :local:
    :depth: 2


High-Level Design
-------------------------

.. raw:: html

   <a href="../../_static/images/architecture/Helios-HighLevelDesign.png" target="_blank">
     <img src="../../_static/images/architecture/Helios-HighLevelDesign.png" alt="Helios high level design" style="height: 512px;" />
   </a>

Communication paths and protocols:

- `Browser → nginx-proxy`: HTTPS (TLS terminated at the proxy).
- `GitHub → nginx-proxy`: HTTPS webhooks (TLS terminated at the proxy).
- `nginx-proxy → client`: HTTP for static assets.
- `nginx-proxy → application-server`: HTTP.
- `nginx-proxy → webhook-listener`: HTTP.
- `application-server → GitHub / Sentry`: HTTPS.
- `webhook-listener ↔ application-server`: NATS (publish / subscribe).
- `application-server → notification-server`: NATS (publish / subscribe).
- `notification-server → Postfix`: SMTP
- `application-server ↔ PostgreSQL`: JDBC.
- `application-server ↔ Keycloak`: OIDC token validation.
- `cadvisor → Prometheus → Grafana`: Prometheus exposition format over HTTP.


Design Goals
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. *Centralization and Integration*: Helios aims to unify fragmented CI/CD processes by offering a centralized platform that consolidates deployment management, test result visualization, and release tracking. This integration reduces cognitive load and eliminates the need to switch between different tools like GitHub Actions and Bamboo.

2. *Modularity and Scalability*: To support evolving requirements and diverse workloads, the system is built using a microservice architecture. Each component—such as webhook processing, notification handling, and authentication—is independently deployable and scalable. This separation of concerns enhances fault tolerance and facilitates continuous evolution of the platform.

3. *User-Centered Interaction*: The design emphasizes accessibility and simplicity across user roles. Whether interacting via the web interface or mobile client, developers, testers, and release managers are provided with intuitive workflows and real-time feedback mechanisms. Features such as environment locking, visual deployment history, and filtered test views support efficient decision-making and reduce onboarding complexity.

4. *Observability and Reliability*: Built-in integration with observability tools like Sentry and Grafana enables real-time monitoring, performance tracking, and failure diagnostics. These capabilities ensure system transparency and support proactive maintenance, which are essential for reliable CI/CD pipelines.

5. *Security and Access Control*: Helios enforces secure access through delegated authentication services and a fine-grained role management system. Permissions are aligned with user responsibilities to minimize the risk of accidental deployments or unauthorized operations.

6. *Interoperability with GitHub Actions*: Rather than replacing existing CI/CD tooling, Helios enhances and extends GitHub Actions via its API and event-driven architecture. This approach preserves compatibility with established workflows while offering additional visibility, analytics, and operational control.


Subsystem Decomposition
-------------------------
At the center, the *Application Server* exposes a small, versioned API and implements every workflow around deployments, release candidates, repository synchronization, and user authorization. It persists authoritative state, publishes domain events, and enforces `RBAC` using information supplied by Keycloak.

The *Webhook Listener* sits at the boundary with GitHub. Its only job is to accept raw webhook payloads, validate their signatures, and translate them into structured domain events that can be routed internally. By keeping this service slim, we isolate the core from bursts of inbound traffic and from any changes in GitHub’s webhook format.

The *Notification Server* handles everything that leaves the system for human consumption (e-mails). It subscribes to notification events and pushes concise, channel-specific notifications. This lets the core remain agnostic of outbound protocols and rate limits.

The *Client* is the user interface, delivered as a web application that speaks only to the Application Server’s API. All user interaction such as deployments, promoting release candidates, inspecting logs go to the *Application Server*.

*Keycloak* provides identity and access management. Users authenticate with Keycloak, receive signed JWTs, and present those tokens to the Application Server. The server’s Auth services validate the token, derive effective roles, and enforce fine-grained permissions.

Finally, *NATS Server* supplies light-weight, at-least-once messaging. Both the *Webhook Listener* and the *Application Server* publish events; both the *Application Server* and *Notification Server* consume them. Because messages flow through a broker rather than direct HTTP calls, the subsystems can be deployed, restarted, or horizontally scaled without tight coupling or cascading failures.

Together these six subsystems form a cohesive yet loosely-coupled whole: a single, authoritative core surrounded by specialized, independently-evolving adapters that connect it to users, external platforms, and infrastructure services.

Application Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Application Server is implemented in Java with Spring Boot and adheres to a classic three-layer architecture. At the outermost edge sits the API layer, a thin facade of REST controllers. These controllers receive HTTP requests from the client, validate authentication tokens issued by Keycloak, perform coarse-grained access checks, and delegate the work to the underlying application services.

The architectural layers are defined as follows:

- `API Layer`: REST controllers that receive HTTP requests, enforce access rules, and delegate to services.

- `Application Layer`: Domain-centric services which contains domain-specific logic.

- `Persistence Layer`: Spring Data repositories that isolate storage concerns from business logic.

Webhook Listener
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Helios ships with a dedicated, lightweight Webhook Listener implemented in `Python`. Its only responsibility is to receive GitHub webhook payloads, verify their signatures, and republish the validated data to a NATS subject for downstream consumption.

By inserting this ingress tier in front of the `Application Server`, the system gains two advantages:

- *Isolation from external traffic*: GitHub’s HTTP callbacks terminate at the `Webhook Listener`, shielding the core from Internet-facing concerns such as rate-spikes or signature-scheme changes.
- *Asynchronous processing*: Once the listener places an event on NATS, the `Application Server` can pick it up on its own schedule, scale consumers horizontally, or replay messages without any coupling to GitHub’s retry behavior.

The listener performs no business logic whatsoever; it simply authenticates, normalizes, and forwards events. This clear separation keeps the trust boundary narrow and the overall architecture resilient.

Client
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

On every build the `Application Server` produces an `OpenAPI` specification; a CI step then runs `@hey-api/openapi-ts`, which turns that spec into strongly-typed Angular DTOs and `TanStack Query` options. Because the client consumes those generated objects directly, API changes surface immediately as `TypeScript` compilation errors, eliminating hand-written boilerplate and speeding up feature work.

The client itself is a single-page application that follows current Angular best-practice guidelines:

- *Angular (zoneless, signal-first)*: The app starts with `Zone.js` disabled and relies on `Angular Signals` for change detection. Components declare reactive state with `signal()` and update the UI by mutating those signals; no global patching or implicit checking is involved.
- *Angular TanStack Query*: Generated hooks fetch and cache data, then expose the results as signals (`query.toSignal()`), which plug straight into template bindings.
- *PrimeNG*: Feature-rich UI widgets (tables, dialogs, trees) imported through lazily loaded feature modules.
- *Tailwind CSS*: Utility-first styling driven by a shared design-token file, giving consistent light/dark themes at no runtime cost.
- *`@hey-api/openapi-ts`*: Converts the OpenAPI spec into TypeScript DTOs and TanStack Query options, enforcing compile-time safety across the HTTP boundary.
- *Tabler Icons*: Lightweight SVG icons included as tree-shakeable ES modules.

This stack delivers a lean, zone-free, signal-based UI layer with automatic type-safety from wire to template and a consistent visual language so new features land quickly and with confidence.

Keycloak
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

User authentication for Helios is delegated to a dedicated `Keycloak` instance. The realm is configured with a single external identity provider which is `GitHub` so every user signs in with their `GitHub` account. Once `Keycloak` completes the OAuth flow it issues a standard OIDC access-token that carries the user’s GitHub handle; the `Application Server` validates this token on each request.

To give users a seamless experience we ship a custom `Keycloak` theme that replaces the default login screens with `Helios` branding and wording. The template files for this theme live in the same monorepo as the others, which means any updates can be developed, reviewed, and versioned through the normal CI pipeline alongside the rest of the code-base.

NATS Server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All inter-service communication in Helios flows through a standalone NATS cluster. Every event, whether it originates in the `Webhook Listener`, the `Application Server`, or another component is published to a subject (`github.events.*`, `notifications.*`) and stored in a JetStream stream with persistence enabled.

Each consumer attaches to its stream with a durable name (`helios-app`, `notification-srv`, etc.). JetStream tracks the last acknowledged sequence for every durable, so if a service is redeployed or temporarily offline it can resume exactly where it left off which brings no duplicate processing and no lost events. This durability also lets operators replay historical data on demand by creating a new durable pointing at an earlier offset.

Using NATS in this way keeps the architecture lightweight yet still provides the safety guarantees more often associated with heavier brokers. Services publish once, consume at their own pace, and scale horizontally simply by adding more consumers with the same durable name; JetStream handles load balancing and back-pressure automatically.

Data Synchronization
-------------------------

While webhook events ensure near real-time data ingestion in Helios, they may occasionally be missed or delayed due to network failures, GitHub delivery issues, or temporary unavailability of the webhook listener. To maintain data integrity and ensure consistency across all repositories, Helios implements a comprehensive data synchronization mechanism using the GitHub REST API.

Data synchronization retrieves a full snapshot of repository-related data, including:

- Repository metadata
- Branches and commits
- Pull requests and their state
- Labels and issues
- Environments and deployment history
- Workflows and workflow runs
- Releases and tags
- Contributor and user profiles

The synchronization process serves two primary goals:

1. *Eventual Consistency*: In the event that webhook delivery fails, data sync ensures that the system eventually reflects the correct repository state.
2. *Daily Freshness*: By running a full scan of all repositories once per day, Helios ensures that its views are always accurate and reflect the most recent activity, including edge cases that may not be covered by webhook events.

There are two triggers for initiating a data sync:

1. *Initial Repository Setup*: When a repository installs the Helios GitHub App, a webhook event is received. This triggers a full data sync to populate all relevant entities in the database for the first time.
2. *Scheduled Daily Sync*: Every night at 4:00 AM, Helios runs a scheduled data sync across all registered repositories. This ensures that the system begins each day with an accurate and up-to-date dataset.

To avoid unnecessary data processing, each sync job tracks the last successful synchronization timestamp and only fetches new or updated records since that point. Additionally, Helios compares API responses with the existing database state to identify and reconcile any discrepancies.

Execution metadata, including sync start and end time, repository ID, and success status, is recorded in the `data_sync_status` table to support auditing and troubleshooting.

This hybrid model—combining webhook-driven ingestion with scheduled API-based synchronization—provides Helios with a resilient, redundant, and up-to-date data foundation.

Access Control
--------------

Helios relies on role-based access control (RBAC) to protect sensitive operations such as deployments and releases. Under RBAC every user is assigned exactly one role, and each role carries a fixed set of permissions. This approach keeps the system easy to reason about and limits accidental misuse, because users can perform only the tasks that match their day-to-day responsibilities.

Access Control Matrix
~~~~~~~~~~~~~~~~~~~~~

.. table:: User Permission Matrix

    +------------------------------------+-----+---------+----------+
    |Feature                             |Guest|Developer|Maintainer|
    +====================================+=====+=========+==========+
    |CI/CD                               |     |         |          |
    +------------------------------------+-----+---------+----------+
    |See PR and Branches                 |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |See Enabled Environments            |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Lock Enabled Environment            |     |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Extend Environment Lock             |     |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Deploy to Enabled Environment (test)|     |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Deploy to Protected Environment     |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |See Test Results for PRs            |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |See Deployment Status               |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |See Stacktrace and logs             |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Unlock Environment (first 30 min)   |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Unlock Environment (after 30 min)   |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Environment Management              |     |         |          |
    +------------------------------------+-----+---------+----------+
    |Enable/disable Environments         |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Determine Deployment Workflow       |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |View Deployment History             |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Project Settings                    |     |         |          |
    +------------------------------------+-----+---------+----------+
    |Define Test Workflows               |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Create Pipeline View                |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Set Lock Expiration Threshold       |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Set Lock Reservation Threshold      |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Sync Workflows with GitHub          |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Release Management                  |     |         |          |
    +------------------------------------+-----+---------+----------+
    |Create Release Candidates           |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |See GitHub Releases List            |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Publish Release Candidate as Draft  |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Add comment on release evaluation   |     |         |✓         |
    +------------------------------------+-----+---------+----------+
    |Test Analytics                      |     |         |          |
    +------------------------------------+-----+---------+----------+
    |See test results                    |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |See test logs                       |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Filter logs by severity             |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+
    |Get insights about test stability   |✓    |✓        |✓         |
    +------------------------------------+-----+---------+----------+




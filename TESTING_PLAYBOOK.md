# Helios — open feature PR testing playbook

A single hand-off doc for testing the four open feature PRs. Use it as a checklist; tick off the scenarios per PR. Each section is independent — pick a PR and go.

## Automated baseline (already done)

| Branch | Server tests | Client tests |
|---|---|---|
| **#1098** `feat/deployment-approval-in-helios` | ✅ 409 tests, 0 failures, 0 errors, 2 skipped | ⏸ Blocked locally on lockfile/policy interaction; runs in CI once `@angular/cdk@21.2.13` ages (~19:30 UTC tonight) |
| **#1046** `feat/queue-monitoring` | ✅ 467 tests, 0 failures, 0 errors, 2 skipped | ⏸ Same |
| **#970**  `feat/deploy-job-detection` | ✅ 394 tests, 0 failures, 0 errors, 2 skipped | ⏸ Same |
| **#1043** `codex/environment-websocket-only` | ✅ 393 tests, 0 failures, 0 errors, 2 skipped | ⏸ Same |

> The automated baseline is good across the board. Manual testing below targets the things automated tests **don't** catch — race conditions, lazy-loading traps, policy edge cases, real GitHub / WebSocket / OpenAI behaviour. Skim each PR's hotspot list before walking its playbook.

## Bringing up your local stack

The infra (Postgres, Keycloak, NATS, Mailhog) is in `compose.yaml`; the application-server itself runs from `./gradlew bootRunDev`. You need a working `.env` (root + `server/application-server/.env`) with your GitHub App credentials. Refer to `docs/contributor/setup.rst` if you haven't done this before.

```bash
# Root infra
docker compose up -d postgres keycloak nats-server mailhog notification

# Server (separate terminal)
cd server && ./gradlew :application-server:bootRunDev

# Client (separate terminal)
cd client && pnpm install --frozen-lockfile && pnpm dev
```

For PRs that change the OpenAPI surface, run `pnpm generate:openapi` after the server is up so the client picks up the new endpoints.

Verify the stack is healthy:
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health   # 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/realms/helios/.well-known/openid-configuration   # 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:4200/                                                  # 200
```

DB peek throughout the playbooks:
```bash
psql -h localhost -U helios -d helios <<'SQL'
\x on
SELECT id, status, auto_approval_decision, auto_approval_at FROM helios_deployment ORDER BY id DESC LIMIT 5;
SELECT id, helios_deployment_id, state, via, reviewer_login, responded_at FROM deployment_approval_request ORDER BY id DESC LIMIT 5;
SQL
```

---

## #1098 — Approval flow (`feat/deployment-approval-in-helios`)

**Authoritative playbook is in the PR body** ([PR #1098](https://github.com/ls1intum/Helios/pull/1098)). Tests 1–8 cover the matrix: auto-approve when deployer is reviewer, deferral + in-app approve, in-app decline, `preventSelfReview`, non-reviewer 403, already-resolved 409, webhook redelivery idempotency, env without reviewers.

Two extra spots worth hammering during your manual pass:

- **Token-exchange failure path.** Temporarily revoke the GitHub IDP-stored token for your Keycloak user (Admin Console → Users → *you* → Identity provider links → github → Remove). Now click **Deploy** as a required reviewer. Expected: auto-approve fails, `DeploymentApprovalRequest.state = FAILED_AT_GITHUB`, `auto_approval_decision = DEFERRED_TO_REVIEWERS`, GitHub workflow stays waiting. The reviewer can still resolve it from `/pending-approvals`. **This is the `noRollbackFor = ResponseStatusException.class` annotation in action** — without that fix the FAILED_AT_GITHUB row gets silently rolled back. Verify by querying `deployment_approval_request` and confirming a row with `state=FAILED_AT_GITHUB, failure_reason` populated.
- **Pessimistic lock contention.** Open two browser sessions as two different required reviewers for the same env. Issue Approve from both at the same moment (or use `curl --parallel`). Expected: one gets 200, the other gets 409 with `This deployment has already been resolved.` Both reviewers' badges clear within ~30 s.

---

## #1046 — Queue monitoring (`feat/queue-monitoring`)

**What the feature does:** durable persistence of every `workflow_job` (today most are dropped), runner inventory tracking, queue-depth signal + ETA prediction, p95-based SLO alerts with quiet hours, in-app queue dashboard.

### Prerequisites

- Your GitHub App webhook must deliver `workflow_job` and `workflow_run` events.
- For the self-hosted runner tests: at least one self-hosted runner registered to the test org/repo.
- Hit `/api/queue/repos/{id}/stats` and `/api/queue/repos/{id}/jobs` once on stack-up to confirm endpoints respond.

### Playbook

#### Q1 — Server restart mid-storm: counter drift vs DB (HIGH RISK)

The hot queue-depth counter lives in Caffeine with 2h/4h TTLs and only in JVM memory. Webhook events delivered across a restart re-enter as "new" → counter under/overcounts until eviction.

1. Kick off 5+ workflow runs in quick succession to stack the queue.
2. Compare `GET /api/queue/repos/{id}/depth` against `SELECT COUNT(*) FROM workflow_job WHERE status='queued' AND repository_id=<id>` — record both.
3. **Hard-restart the server** (`pkill -f bootRunDev` and re-bootRunDev). While it's down, fire 2 more workflow runs.
4. After restart, repeat the comparison. **Expected:** they should match. **Likely real:** they will drift, possibly by 1–2 in either direction.
5. Optional: trigger GitHub's "Redeliver" on a `workflow_job: in_progress` webhook for a job whose `queued` event was processed pre-restart. Confirm whether the counter goes negative (the `counter.get() > 0` guard should prevent it).

#### Q2 — Multi-replica admin backfill: GitHub rate-limit blowup

`AtomicBoolean running` in `WorkflowJobBackfillService` is JVM-local, not cluster-wide. If you run multiple replicas on staging, two admins can start a backfill on different pods simultaneously.

1. On staging with 2+ replicas behind the LB: get a sticky-session cookie pinned to pod A.
2. `curl --resolve` to force a request to pod B with a different admin's token.
3. Trigger `POST /api/queue/admin/backfill` on both pods within seconds.
4. **Expected:** one pod should reject. **Likely real:** both run; watch GitHub abuse-detection (secondary rate limit) get hit at ~360 req/min combined.
5. Acceptable mitigation: a single-replica deploy, or a Redis-based lock. Not blocking the PR but worth flagging in the PR comments.

#### Q3 — Alert quiet-hours timezone confusion + dropped close events

`QueueAlertEvaluator.inQuietWindow` uses `LocalTime.now()` of the JVM, which on most K8s pods is UTC. Set up: a user in Munich (UTC+2) configures quiet hours `22:00–06:00` thinking local time. The alert evaluator interprets it as UTC, so the quiet window is actually `00:00–08:00` Munich time.

1. Create a `QueueAlertRule` with quiet window `22:00–06:00`.
2. Force a P95 breach by inserting fake rows into `queue_wait_stat` for the current hour. Check:
   - If the JVM's `LocalTime.now()` is currently inside `22:00–06:00`: confirm **no** alert event is created.
   - Otherwise: confirm an event **is** created.
3. Now: while a `QueueAlertEvent` is OPEN and the breach clears, manipulate the system clock or rule so the evaluator's next tick runs inside the quiet window. Confirm whether the `closeEvent` path runs (it probably does NOT, because the evaluator `continue`s past the rule entirely during quiet hours). **Likely real bug:** an open event survives quiet hours and only closes when the resolution email is also suppressed → the user misses both the open and close signals overnight.

#### Q4 — `/jobs?limit=200` performance on a full queue

`QueueEtaService` is called once per job inside the list-render loop with a 3s cache keyed by job id. A cold cache + a long queue = N × 3 queries per request.

1. Queue 50+ jobs spanning at least 3 different label sets (e.g., `ubuntu-latest`, `self-hosted, linux, x64`, `self-hosted, gpu`).
2. Cold-cache: restart the server, then immediately hit `GET /api/queue/repos/{id}/jobs?limit=200`. Time the response. **Expected:** <2 s. **Watch for:** >5 s → table loading spinner in the UI; also DB connection pool exhaustion under 5 concurrent users.

#### Q5 — Runner inventory: deregistered runner stays "online"

When a self-hosted runner is removed via the GitHub UI, the `workflow_job.completed` webhook for whatever it was running may arrive, but `RunnerInventoryReconciler.reconcile` may not flip the runner's `status` to OFFLINE.

1. Register a self-hosted runner to your test repo.
2. Confirm it appears in `GET /api/queue/repos/{id}/runners` with `status=ONLINE`.
3. Remove it via GitHub UI (Settings → Actions → Runners → remove).
4. Wait 60 s, then re-check. **Expected:** `status=OFFLINE`. **Likely real:** still ONLINE; the reconciler only updates seen runners and doesn't sweep unseen ones.

#### Q6 — UI confirmation of all of the above

- Open the queue dashboard page. Confirm the queue-depth count shown matches the DB.
- Configure a Queue P95 Breach alert. Send yourself a test (use Mailhog at `http://localhost:8025` to capture).
- Trigger the email by inserting a synthetic high-wait row; check the email arrives once, **not** repeatedly.
- After deleting the alert rule, confirm any open events for it cascade-delete.

---

## #970 — AI-based deployment job detection (`feat/deploy-job-detection`)

**What the feature does:** a "Detect" button in the project-settings workflow row sends the workflow YAML to OpenAI (via spring-ai), and the model returns the suggested deployment-job name to pre-fill the config.

### Prerequisites

- `helios.ai.enabled=true` + a working `OPENAI_API_KEY` (or your proxy URL like the TUM Logos one) in your `.env`.
- A repo with multiple workflows: one with an obvious deployment job, one with no deployment, one with ambiguous candidates.

### Playbook

#### A1 — Hallucinated job name not validated (HIGH RISK)

The detector returns whatever the LLM produces. If the LLM hallucinates a job name that doesn't exist in the workflow, the UI may save it verbatim.

1. Open project-settings → workflow row → click **Detect**.
2. Note the returned `deploymentJobName` and the actual job keys in the workflow YAML.
3. If they match exactly → fine. If they don't → click **Save**. Then check `deployment_workflow_config.deploy_job_name` in the DB.
4. **Expected behaviour worth aspiring to:** if the returned name isn't an actual job key, the UI either rejects the save with a clear error or marks the row as "needs manual verification".
5. **Likely real:** the bad name is saved as-is, and downstream deployment-tracking later silently misses the deploy job (it doesn't exist).

Try to force a hallucination by pointing at a workflow with jobs named things like `setup-deploy-cache` (no real deploy job).

#### A2 — Workflow YAML >30 KB silent truncation

`OpenAiWorkflowDeploymentJobDetector` truncates user prompt at 30,000 chars. Long composite workflows easily exceed.

1. Point at a workflow >30 KB where the obvious deploy job is in the last 5 KB of the file.
2. Click **Detect**. **Expected good behaviour:** UI surfaces "workflow too long, partial analysis only".
3. **Likely real:** the model returns `NOT_FOUND` or a wrong job from the early sections, with no truncation warning.

#### A3 — Concurrent detection: spinner / error attribution swap

`detectingWorkflowId` is a single signal in the project-settings component.

1. Throttle network in DevTools (Slow 3G).
2. Click **Detect** on workflow A. Immediately click **Detect** on workflow B.
3. Watch the row spinners and any error toasts. **Expected:** A's result attaches to A, B's to B. **Likely real:** the spinner on B clears prematurely (A's `onSettled` overwrites the signal), and an A-side error appears on B's row.

#### A4 — No rate limit / cost protection

The endpoint is `@EnforceAtLeastMaintainer` (good), but a single maintainer can spam clicks with no throttling. Each click is a ~30 KB prompt.

1. Open DevTools → Network. Click **Detect** 20 times rapidly.
2. **Expected:** at most one request in flight at a time, OR a debounced retry. **Likely real:** all 20 hit the backend; OpenAI is called 20 times. Note this for the PR comments; not blocking but worth a follow-up rate limit.

#### A5 — `AiProperties.enabled=false` graceful path

1. Set `helios.ai.enabled=false` in your dev `.env` and restart.
2. Open project-settings. **Expected:** the **Detect** button is either hidden or disabled with a tooltip explaining AI is off. **Likely real:** the button is enabled, click yields a generic `ERROR` toast that looks indistinguishable from a transient failure.

---

## #1043 — Environment-deployment WebSocket updates (`codex/environment-websocket-only`)

**What the feature does:** when a Helios-managed deployment changes state, the server publishes a websocket message to all clients subscribed to that env's repo. Clients invalidate their TanStack-Query env-state cache and re-fetch — UI updates in real time without polling.

> ⚠️ Still a draft PR, author absent. Substantial diff (28 files, +1k LOC) including a STOMP handshake, repo-scoped subscription validation, ConcurrentWebSocketSessionDecorator buffering. This is the most failure-prone PR of the four.

### Prerequisites

- Two browser sessions/profiles, two different Helios users.
- DevTools network tab with the WS-frame inspector handy.

### Playbook

#### W1 — Cross-repo subscription rejection (AUTHZ; HIGH RISK)

A user with no GitHub access to repo R must not be able to receive R's env-deployment events.

1. As user U1 (no role on repo R), sign in to Helios.
2. Open DevTools → Application → service workers (or directly DevTools → Network → WS). Find the `/ws/environments` connection.
3. Send a malformed subscribe frame for repo R (use the WS frame inspector to craft).
4. **Expected:** server closes the socket with `POLICY_VIOLATION` (1008) **before** any payload is delivered.
5. **Confirm:** no `environment-deployment-invalidated` frame ever arrives.

#### W2 — Token-revocation mid-session (AUTHZ STALENESS; HIGH RISK)

The handshake validates the JWT once; the user's repo role is checked at subscribe time but not re-checked thereafter.

1. As U1 (with `READ` on repo R), open the env page for R. Confirm WS frames arrive.
2. In another tab, revoke U1's GitHub access to R (e.g. remove them from the repo collaborators).
3. Trigger a deployment on R. **Expected good:** the next WS event for R should be denied. **Likely real:** events keep flowing until U1 closes the socket (logout, refresh, tab close).
4. This is by design today; flag it in the PR comments so a follow-up can re-validate per message or close the socket on role revocation events.

#### W3 — Rolled-back transaction publishes a WS event (SILENT FAILURE)

`DeploymentService` IO_ERROR path: the publish happens *inside* the catch block via `publishAfterCommit`, but the surrounding transaction is about to roll back.

1. Configure your dev `.env` with a `WORKFLOW_FILE_NAME` that doesn't exist in the repo so `workflow_dispatch` fails.
2. Click **Deploy** on an env. The server-side `DeploymentService.deployToEnvironment` will hit the `IOException` branch and `throw DeploymentException`.
3. **Watch:** does a WS `environment-deployment-invalidated` frame still leak to the client? **Likely real:** yes, because `afterCommit` synchronization runs against whatever was committed (and the transaction was rolled back, so no commit ever ran — but if the publisher detects `isSynchronizationActive() == false`, it broadcasts immediately).
4. **Confirm:** after the failed Deploy, the client doesn't show a stale "deploying" state forever; the next refetch returns the DB's actual (PENDING, no helios_deployment) state.

#### W4 — Rapid repo switch leaks WebSocket connections

1. Open the env dashboard for repo A.
2. Quickly switch to repo B in the repo selector, then immediately back to A. Repeat 3–4 times.
3. DevTools → Network → WS column. **Expected:** at most one live WS connection at a time. **Likely real:** transient overlapping connections; in worst case the per-tab connection count grows.

#### W5 — Token expiry busy-loop

The client retry uses `retry({ delay: ... })` with the Keycloak token captured at `openSocket()` time. If the access token expires mid-session and the server returns 401 on reconnect, the loop keeps trying with the stale token.

1. Configure Keycloak access token lifespan to a very short value (e.g. 1 min) for the test.
2. Leave a Helios tab idle for >2 min so the access token expires.
3. Trigger a deployment from another tab so the idle tab should receive a WS event.
4. **Expected:** the client either silently refreshes the token or shows a "session expired, please refresh" prompt. **Likely real:** the WS reconnect spams the server with 401s every backoff cycle.

#### W6 — Buffer-overflow session kill under burst load

`WebSocketSessionRegistry` uses `ConcurrentWebSocketSessionDecorator` with a 64 KB send buffer and 5 s send timeout. A slow client (DevTools JS paused) during a CI storm will be evicted.

1. Open the env page, hit a DevTools JS breakpoint to pause execution.
2. Have a colleague trigger 20+ deployments in 10 s on that repo.
3. Wait 6+ seconds, then resume JS. **Expected:** the WS reconnects cleanly; the env page refetches and shows current state. **Watch for:** silent UI staleness (the page shows the pre-storm state).

#### W7 — Malformed-frame robustness

1. From DevTools, inject a malformed inbound WS frame (e.g. `{"type":"FAKE","junk":true}`).
2. **Expected:** the server responds with a `bad-request` frame and keeps the connection open.
3. **Watch for:** server-side stack trace, dropped connection, or any other ungraceful response.

---

## Smoke-test commands (once your stack is fully booted)

Replace `{REPO_ID}`, `{DEPLOYMENT_ID}`, etc. with your test values. `$TOKEN` is your Keycloak access token (grab from DevTools → Application → Cookies or Authorization header on any authenticated request).

```bash
# #1098 — approval endpoints
curl -i http://localhost:8080/api/deployments/pending-approvals \
  -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://localhost:8080/api/deployments/{DEPLOYMENT_ID}/approve \
  -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://localhost:8080/api/deployments/{DEPLOYMENT_ID}/decline \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"comment":"wrong branch"}'

# #1046 — queue endpoints
curl -i "http://localhost:8080/api/queue/repos/{REPO_ID}/depth"
curl -i "http://localhost:8080/api/queue/repos/{REPO_ID}/jobs?limit=50"
curl -i "http://localhost:8080/api/queue/repos/{REPO_ID}/runners"
curl -i "http://localhost:8080/api/queue/repos/{REPO_ID}/stats"

# #970 — AI deploy-job detection
curl -i -X POST "http://localhost:8080/api/settings/{REPO_ID}/workflows/{WORKFLOW_ID}/detect-deployment-job" \
  -H "Authorization: Bearer $TOKEN"
```

For #1043 — open the env page in a browser, then watch DevTools → Network → WS for frame traffic. (curl can't really exercise STOMP frames without a STOMP client; `wscat` works if you craft the handshake.)

---

## Priority order if you only have time for one thing per PR

| PR | If you only test one thing | Why |
|---|---|---|
| **#1098** | Token-exchange failure path + pessimistic-lock race | These exercise the `noRollbackFor` + `SELECT FOR UPDATE` machinery added in the review-fix commit. Most of the rest is covered by automated tests. |
| **#1046** | Q1 (counter drift after restart) | The biggest in-production failure mode; cheap to test. |
| **#970** | A1 (hallucinated job name validation) | Highest user-impact bug if it exists. |
| **#1043** | W1 (cross-repo subscription) + W3 (rolled-back WS event) | Auth bypass and silent staleness — the two failure modes that matter. |

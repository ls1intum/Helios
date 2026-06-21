# Runbook: reconfigure Artemis test display for the new `CI` workflow

**Apply this after PR #1159 is deployed to prod.** It is a one-time Helios webapp
configuration change for the `ls1intum/Artemis` repository — no code or restart needed.

## Why

Artemis moved from chained, per-workflow CI to a single **`CI`** orchestrator
(`.github/workflows/ci.yml`) that calls reusable workflows (`ci-test.yml`,
`ci-e2e.yml`, …). As a result:

- All JUnit artifacts are now attached to the top-level **`CI`** run, with **new names**.
- Helios matches test results by `TestType { workflow, artifactName }`. The old test
  types still point at the retired "Tests"/"E2E Tests" workflows, so nothing is
  ingested → the PR/branch **Test Results** view is empty.

PR #1159 already handles the code side (a completed run with no matching artifact is
treated as "no results" instead of a red failure, and `artifactName` now supports a
`*` wildcard). This runbook is the **configuration** half: re-point the test types at
the `CI` workflow.

## Artifact reference (verified against Artemis `main`)

| Test type | Artifact name (exact) | File inside | Parser support |
|---|---|---|---|
| Server unit | `Server JUnit Test Results` | `TEST-*.xml` | ✅ |
| Client unit | `Vitest JUnit Test Results` | `junit.xml` | ✅ |
| E2E (both phases) | `JUnit Test Results Phase *` | `results.xml` | ✅ |

Artifact names are **case- and space-sensitive** — copy them verbatim. Helios's JUnit
parser only reads files named `TEST-*.xml`, `results.xml`, or `junit.xml`; all three
artifacts above comply.

## Prerequisites

- **Maintainer** role (or higher) on the Artemis repository in Helios.
- PR **#1159 deployed** to prod (needed for the `*` wildcard and the "no results ≠
  failure" behavior). Without it, use the two-row E2E fallback in Step 3.
- The **`CI`** workflow (`ci.yml`) is visible in Helios (it appears after the first
  `CI` run syncs; otherwise click **Fetch latest workflows** in Step 1).

## Step 1 — Open repository settings

1. Open the **Artemis** repository in Helios.
2. Left sidebar → **Repository Settings** (adjustments/gear icon, Maintainer+ only).
   URL is `…/repo/<id>/settings`; everything below applies only to this repository.
3. Scroll to **Workflows**. If there is no row with **Workflow Name = `CI`**
   (File Name `ci.yml`), click **Fetch latest workflows** and wait for the refresh.

## Step 2 — Label the `CI` workflow as a test workflow (organizational)

1. In the **Workflows** table, find the row **Workflow Name = `CI`** (`ci.yml`).
2. In its **Label** column, select **`TEST`**.
3. Confirm in the **Change Label** dialog ("Workflow Label updated successfully").

> Ingestion is actually triggered by the **test types** in Step 3, not the label.
> Labeling `CI` as `TEST` keeps the UI consistent and is harmless, but not strictly
> required.

## Step 3 — Add the three test types

In **Test Types** → **Add Test Type**. For each row: set **Name**, set **Artifact
Name** (exact), pick **Workflow = `CI`**, click **Add**.

| Name (free choice) | Artifact Name (exact) | Workflow |
|---|---|---|
| `Server Tests` | `Server JUnit Test Results` | `CI` |
| `Client Tests` | `Vitest JUnit Test Results` | `CI` |
| `E2E Tests` | `JUnit Test Results Phase *` | `CI` |

- The E2E value ends with a space then `*`; the wildcard matches both
  `JUnit Test Results Phase 1` and `… Phase 2`, so one row covers both phases.
- **Fallback (only if #1159 is NOT deployed):** create two rows instead of the E2E row
  — exact names `JUnit Test Results Phase 1` and `JUnit Test Results Phase 2`.
- `Name` must be unique within the repository (the form rejects duplicates).

## Step 4 — Remove stale test types

In the **Test Types** table, delete (trash icon → confirm) every row whose **Workflow**
column is **not** `CI` (i.e. the old "Tests"/"E2E Tests" workflows). Optionally set those
old workflows' **Label** back to `NONE`.

## Step 5 — Verify

1. Open or re-run a PR that touches server/client/E2E code so `CI` produces the artifacts.
2. After `CI` completes, open that PR/branch in Helios — **Test Results** should populate
   (Server / Client / E2E).
3. Docs-only PRs (where `CI` skips the test jobs) correctly show **no results**, not a red
   failure.
4. If a configured type unexpectedly shows nothing, check the server log:
   `No matching test artifacts for workflow run …; Artifacts present: [...]; configured
   test-type artifact names: [...]` — it prints what the run produced vs. what's
   configured, which pinpoints a typo'd Artifact Name.

## Rollback

Configuration-only and non-destructive: delete the three new test types and (if set)
revert the `CI` workflow label to `NONE`. No deploy required.

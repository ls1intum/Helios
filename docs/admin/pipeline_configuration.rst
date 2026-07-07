=============================
Configuring the Pipeline View
=============================


.. contents:: Content of this document
    :local:
    :depth: 2


Overview
--------

The **pipeline view** on a branch or pull request shows a fixed set of *canonical nodes* —
the CI stages you care about most — grouped into categories. Out of the box these are:

- **Build**: Native, Docker
- **Tests**: Client, Server, E2E
- **Quality**: Client, Server

Every configured node is **always visible**. A node whose CI job has not started yet (or does
not run for a given change) is rendered as *pending* (a dashed, non-spinning icon) rather than
being hidden. This is the key difference from the previous behaviour, where a stage only appeared
once a matching workflow run existed — so the most interesting stages (notably *Build*) were
frequently missing.

Nodes are declared in configuration and **mapped by name** to the actual GitHub Actions jobs, so
the catalog can be adapted to any CI layout without code changes.


How it works
------------

1. Helios ingests GitHub Actions ``workflow_job`` events and stores each job (name, status,
   conclusion, timestamps) linked to its ``workflow_run``.
2. For a branch/PR, Helios resolves the latest run(s) on the head commit and loads their jobs.
3. Each configured node matches the jobs whose **name starts with** any of its
   ``job-name-matchers`` (case-insensitive), optionally restricted to a workflow via
   ``workflow-name-matcher``.
4. The matched jobs are aggregated into a single node status (see `Status aggregation`_). A node
   with **no** matching job is reported as ``PENDING``.

.. note::

   The catalog is **global** (one shared configuration, not per repository). For a repository
   whose CI uses different job names, unmatched nodes simply stay *pending*. Tune the matchers to
   the CI you actually monitor.


Configuration
-------------

Nodes are defined under ``helios.pipeline`` in
``server/application-server/src/main/resources/application.yml`` (or any Spring configuration
source — an ``application-*.yml`` overlay or environment variables). The shape is a list of
**categories**, each with an ordered list of **nodes**:

.. code-block:: yaml

  helios:
    pipeline:
      categories:
        - name: "Build"                    # category title shown as a panel header
          nodes:
            - key: "build-native"          # stable, unique identifier for the node
              label: "Native"              # text shown next to the status icon
              job-name-matchers:           # any prefix match (case-insensitive) selects the job
                - "Build / Build .war artifact"
            - key: "build-docker"
              label: "Docker"
              job-name-matchers:
                - "Build / Build and Push Docker Image"
        - name: "Tests"
          nodes:
            - key: "test-client"
              label: "Client"
              job-name-matchers: ["Test / Client Tests"]
            - key: "test-server"
              label: "Server"
              job-name-matchers: ["Test / Server Tests"]
            - key: "test-e2e"
              label: "E2E"
              job-name-matchers: ["E2E /"]
        - name: "Quality"
          nodes:
            - key: "quality-client"
              label: "Client"
              job-name-matchers: ["Quality / Client Code Style"]
            - key: "quality-server"
              label: "Server"
              job-name-matchers:
                - "Quality / Server Code Style"
                - "Quality / Server Code Quality"

Field reference
~~~~~~~~~~~~~~~

``categories[].name``
  Category title, rendered as the panel header. Categories appear in declaration order.

``categories[].nodes[].key``
  Stable, unique identifier for the node (used by the client for tracking). Keep it stable across
  edits.

``categories[].nodes[].label``
  Human-readable text shown next to the status icon.

``categories[].nodes[].job-name-matchers``
  A list of **name prefixes**. A GitHub Actions job matches the node when its name starts with any
  of these (case-insensitive). Prefix matching means ``"Build / Build and Push Docker Image"``
  also matches the matrix/variant job ``"Build / Build and Push Docker Image (PR, amd64)"``.
  Multiple matchers on one node are OR-ed, and all matching jobs are aggregated together.

``categories[].nodes[].workflow-name-matcher`` (optional)
  When set, a job only matches if its **workflow name contains** this substring (case-insensitive).
  Use it to disambiguate identically-named jobs that live in different workflows — e.g. constrain
  a node to the main ``CI`` orchestrator run.


Finding the right job names
---------------------------

The matchers must correspond to the **job names GitHub emits**, which are what the pipeline
receives on ``workflow_job`` events.

- For a normal job, the name is the job's ``name:`` (or its job id if ``name:`` is omitted).
- For a **reusable workflow** call (``jobs.<id>.uses: ./.github/workflows/…``), GitHub prefixes
  the reusable workflow's internal job names with the **caller job name**, joined by `` / ``. So a
  caller job named ``Build`` that calls a reusable workflow whose internal job is named
  ``Build .war artifact`` produces the job name ``Build / Build .war artifact``.
- **Matrix** legs append the matrix values in parentheses, e.g.
  ``Build / Build and Push Docker Image (PR, amd64)`` — which is why matching is prefix-based.

To discover the exact names for a repository, open a recent run in the GitHub Actions UI and read
the job names in the left-hand job list, or inspect the ``.github/workflows`` definitions.

The defaults above target Artemis' single ``CI`` orchestrator run
(``ls1intum/Artemis`` ``.github/workflows/ci.yml``), whose ``Build`` / ``Test`` / ``Quality`` /
``E2E`` jobs call reusable workflows and therefore surface as ``<Stage> / <Job>``.


Status aggregation
------------------

For each node, the matched jobs are reduced to one human-legible state, chosen so the earliest
actionable signal is never hidden and a busy branch never looks idle:

- **Running** (``IN_PROGRESS``, spinner) — at least one matching job has started while others are
  not yet done.
- **Failed** (``FAILURE``, red) — *fail-fast*: as soon as **any** matching job fails, times out, or
  has a startup failure, the node is red, **even while slower legs keep running**. The node then
  links to the failing job (not an arbitrary passing one).
- **Passed / Skipped / Cancelled** — once **all** matching jobs are terminal, the *conclusion* is
  the worst-wins result across them:

  - **CANCELLED** if any job was cancelled;
  - else **SKIPPED** if every job was skipped (e.g. gated out by change-detection on this change);
  - else **SUCCESS** if any job succeeded;
  - else **NEUTRAL**.

When a node has **no matching job yet**, its state is inferred from the CI *run* it belongs to,
rather than a permanent "not running yet" — so a node is honest about what is actually happening:

- **Running** (``IN_PROGRESS``, spinner) — the CI run is executing but this stage's job hasn't been
  ingested yet (job events routinely lag the run on a busy CI). The node mirrors the run so an
  active pipeline reads as running rather than idle; the exact per-stage state backfills as the
  job's events arrive.
- **Queued** (``QUEUED``, muted clock) — the run is queued and nothing has started yet. Scheduled,
  not idle, and visually distinct from the running spinner.
- **Waiting for approval** (``ACTION_REQUIRED``, amber pause) — the run is gated on a maintainer's
  approval (e.g. a first-time contributor). Actionable, not a silent blank.
- **No result** (``NEUTRAL``) — the run finished but this job never appeared. We cannot tell an
  intentional skip from a webhook event we simply never ingested, so we make the weaker, honest
  claim rather than asserting the job was skipped.
- **Not running yet** (``PENDING``, dashed) — only when **no** CI run matches the node at all.

The client renders a distinct icon per state: dashed circle (not running yet), muted clock (queued),
amber pause (waiting for approval), yellow spinner (running), red ✗ (failed), green ✓ (passed),
grey ⊝ (skipped), grey ⃠ (cancelled), grey ? (no result / neutral).


Freshness and the previous commit
---------------------------------

The pipeline reflects the branch/PR **head commit**, resolved purely from ingested webhook runs
(no GitHub API calls). Two behaviours keep it trustworthy on fast-moving branches:

- The header shows **which commit** the states are for and whether it is the head. If the newest
  commit has no CI run yet (just pushed, gated, or a missed push webhook), the most recent commit
  that *did* run is shown instead, clearly flagged *"newest commit not built yet"*.
- While the displayed commit is still running, a footer summarises the **last built commit's**
  outcome (e.g. *"last built commit abc1234: passed"*) for at-a-glance confidence. It is labelled
  "last built" rather than "previous" because it is the newest commit that ran — after a rebase or
  a re-run on an older commit, that is not necessarily this commit's parent. It disappears once the
  displayed commit is terminal.

.. note::

   Freshness is only as current as the branch head Helios has ingested. The head is updated by
   push webhooks and the periodic repository sync, so a *missed* push webhook can briefly leave the
   view a commit behind until the next sync heals it — the displayed commit SHA is always shown so
   this stays visible rather than silent. Tightening this window (a targeted head refresh on the
   reconciliation cadence) is a natural follow-up.


Merge gate
----------

An optional ``helios.pipeline.gate`` node maps to the CI's single required-checks job — the one job
that reflects "can this PR merge" (for Artemis, ``All required CI Passed``). It is aggregated with
the same state rules as any node but rendered as a **badge next to the pipeline header** rather than
inside a category, so merge-readiness is visible at a glance. Omit the key to hide the badge.

.. code-block:: yaml

  helios:
    pipeline:
      gate:
        key: "ci-gate"
        label: "All required CI passed"
        job-name-matchers: ["All required CI Passed"]
        workflow-name-matcher: "CI"


Adding a node
-------------

To add, for example, a *Documentation* quality node that maps to a ``Build Documentation`` job:

.. code-block:: yaml

  helios:
    pipeline:
      categories:
        # … existing categories …
        - name: "Docs"
          nodes:
            - key: "docs-build"
              label: "Build"
              job-name-matchers: ["Build Documentation"]

1. Add the category/node under ``helios.pipeline.categories``.
2. Ensure the ``job-name-matchers`` match the emitted job name(s) (see above).
3. Redeploy the application server so the new configuration is loaded (see
   :doc:`troubleshooting`). No database migration or client change is required — the client renders
   whatever the ``/api/pipeline`` endpoint returns.


Applying changes
----------------

``helios.pipeline`` is ordinary Spring configuration and is read at startup. After editing the
YAML (or the corresponding environment overlay), restart/redeploy the application server for the
change to take effect. Because the client consumes the canonical structure from the server, no
client rebuild is needed to add, remove, or relabel nodes.

=============================
Configuring the Pipeline View
=============================


.. contents:: Content of this document
    :local:
    :depth: 2


Overview
--------

The **pipeline view** on a branch or pull request shows the CI stages as a fixed set of nodes,
grouped into stages. Every repository gets the default lanes **Build → Test → Quality**, and the
nodes inside each lane (e.g. Native, Docker, Client, Server, E2E) are **configured per repository
and stored in the database** — editable by repository maintainers in the project settings.

Each node is *always visible*: it is shown as pending before its CI job starts rather than being
missing entirely. A node maps to one or more GitHub Actions jobs by **name matcher**.

Helios can **auto-detect** a repository's nodes from its observed CI jobs, so maintainers get a
sensible configuration without hand-typing matchers.


How it works
------------

1. Helios ingests GitHub Actions ``workflow_job`` events and stores each job (name, status,
   conclusion) linked to its ``workflow_run``.
2. For a branch/PR, Helios resolves the head-commit runs and loads their jobs.
3. The repository's configured nodes are matched against those jobs: a node matches a job when the
   job name **starts with** any of the node's matchers (case-insensitive) and, if set, the job's
   workflow name **contains** the node's workflow filter.
4. Each node's matching jobs are aggregated into a single status (worst-wins conclusion); a node
   with no matching job yet is shown as **pending**.

If a repository has **no** saved configuration, Helios serves an **auto-detected** default (the
Build/Test/Quality lanes populated from the repo's observed jobs). Saving in the UI persists an
explicit configuration that from then on drives the view.


Configuring in the UI
---------------------

Repository maintainers configure the pipeline under **Project settings → Pipeline configuration**
(the settings page is maintainer-guarded):

- **Stages** (Build/Test/Quality by default): add, rename, reorder, or remove a stage lane.
- **Nodes**: within a stage, add/edit/remove a node. A node has a **label** (shown in the pipeline),
  one or more **job-name matchers** (comma-separated name prefixes), and an optional **workflow name
  filter** (to disambiguate identically-named jobs across workflows).
- **Reorder** stages and nodes with the up/down controls.
- **Auto-detect**: proposes stages/nodes from the repository's observed CI jobs; review the
  suggestion and **Save** to apply (auto-detect never overwrites what is saved until you save).
- **Save** persists the whole configuration.

Nothing is persisted until **Save**; editing (including Auto-detect) only changes the working copy.


Matching semantics
------------------

A node matches a GitHub Actions job when:

- the **job name starts with** any of the node's ``jobNameMatchers`` (case-insensitive) — prefix
  matching means ``"Build / Build and Push Docker Image"`` also matches the matrix leg
  ``"Build / Build and Push Docker Image (PR, amd64)"``; and
- if a **workflow name filter** is set, the job's workflow name contains it.

For a **reusable workflow** call, GitHub emits job names as ``<caller job> / <inner job>`` (e.g.
Artemis' single ``CI`` orchestrator surfaces ``Build / …``, ``Test / …``, ``Quality / …``,
``E2E / …``). Matchers therefore use the emitted ``<Stage> / <Job>`` name.


Auto-detection
--------------

Auto-detect reads the distinct job names Helios has observed for the repository and classifies them:

- **Reusable-workflow prefix** ``"<Stage> / <Job>"`` → the stage becomes the lane, the inner job the
  node (high confidence).
- **Keyword classification** for flat job names: ``build``/``compile``/``package``/``docker`` →
  Build; ``test``/``spec``/``e2e``/``cypress`` → Test; ``lint``/``style``/``checkstyle``/``eslint``/
  ``sonar``/``codeql`` → Quality.
- Matrix-leg suffixes like ``(PR, amd64)`` are stripped so legs collapse to one node whose matcher
  is the common prefix.
- Jobs that don't classify are grouped under an editable **Other** stage.

Detection improves as more ``workflow_job`` events accrue; re-run Auto-detect any time to refresh
the suggestion.


API reference
-------------

The UI uses these endpoints (per repository, under ``/api/settings/{repositoryId}``):

- ``GET /pipeline-config`` — the saved configuration, or the auto-detected default when none is
  saved (read-only; never writes).
- ``PUT /pipeline-config`` — replace the saved configuration (maintainer only).
- ``GET /pipeline-config/suggestions`` — the auto-detected suggestion (maintainer only).

The pipeline view itself is served by ``GET /api/pipeline/branch`` and
``GET /api/pipeline/pr/{pullRequestId}`` (tenant-scoped via the ``X-REPOSITORY-ID`` header).

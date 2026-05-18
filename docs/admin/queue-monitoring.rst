Queue monitoring (GitHub Actions)
=================================

Helios consumes the ``workflow_job`` event today; the queue-monitoring feature additionally relies
on the org-level ``self_hosted_runner`` event and the org runner inventory REST endpoint.

GitHub App permissions
----------------------

The Helios GitHub App needs the following **in addition to** what it already has:

- **Subscribed events**: add ``self_hosted_runner`` (org-level event).
- **Organization permissions**: add ``administration:read`` (required for
  ``GET /orgs/{org}/actions/runners``).
- **Repository permissions**: ``actions:read`` is already present and unchanged.

Existing installations must re-authorize after the permission change.

Configuration
-------------

The feature is gated by ``helios.queue.enabled`` (defaults to ``false``). Other relevant
properties (see ``application-*.yml``):

.. code-block:: yaml

    helios:
        github:
            org: ls1intum
            apiBaseUrl: https://api.github.com
        queue:
            enabled: true
            eta:
                githubHostedConcurrencyCeiling: 20
            reconcile:
                runner:  { fixedRateMs: 60000 }
                jobs:    { fixedRateMs: 30000 }
                stuck:   { fixedRateMs: 60000 }
                rollup:  { fixedRateMs: 300000 }
                alerts:  { fixedRateMs: 30000 }

Rollout
-------

After enabling the feature, trigger a one-shot 30-day backfill via:

.. code-block:: shell

    curl -X POST https://helios.aet.cit.tum.de/api/queue/admin/backfill \
         -H "Authorization: Bearer <token>"

The backfill self-throttles to 180 req/min and pauses on 429 / low rate-limit remaining.

Rate-limit budget
-----------------

All REST calls flow through ``GitHubFacade``/``GitHubRestClient`` which transparently sends
``If-None-Match`` (using the ``EtagCache``) and treats ``304`` as a no-op. Steady-state load
should sit around 40% of the 5000-req/hour core limit. The metric
``helios.github.rest.ratelimited`` fires on any 429/403; ``helios.github.rest.304`` lets you
verify that ETag conditional GETs are working.

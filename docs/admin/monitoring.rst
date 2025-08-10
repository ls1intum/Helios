========================
Monitoring
========================


.. contents:: Content of this document
    :local:
    :depth: 2



Overview
-----------------

This page explains how we monitor the production Helios deployment today. We currently rely on:

- Grafana for metrics, dashboards and ad‑hoc investigations.
- Sentry for error tracking (client and backend).


Links
-----------------

- Grafana: https://grafana.gchq.ase.in.tum.de/  (login via Keycloak)
- Sentry (client): https://sentry.aet.cit.tum.de/organizations/aet/projects/helios-client/?project=4
- Sentry (backend): https://sentry.aet.cit.tum.de/organizations/aet/projects/helios-server/?project=5


Access & Permissions
-----------------------

To get full access, please do both of the following.

1. **Sentry access**: Ask the admins to **add you to the relevant Sentry group** with permissions over the Helios projects.
2. **Grafana access**: Ask the admins to **add you to the Helios dashboard** since Helios dashboard is not publicly accessible.

.. _artemis:

======================================================
Helios: Enhanced UI for GitHub Actions
======================================================

The personification of the sun, symbolizing light, clarity, and visibility that this app brings to CI/CD processes and test reliability

.. raw:: html

   <a href="./_static/images/helios-icon-1280x640.png" target="_blank">
     <img src="./_static/images/helios-icon-1280x640.png" alt="Helios the Sun God" style="height: 250px;" />
   </a>

Introduction
------------

The Artemis open-source project as well as other projects at the AET chair rely on GitHub Actions for its CI/CD workflows but faces challenges such as complex workflows, lack of user-friendly deployment management, and limited test analytics. This project aims to enhance the CI/CD capabilities of Artemis by developing a centralized web application that integrates with GitHub Actions. The proposed solution will provide a streamlined platform for managing deployments, environment tracking, and test analytics, accessible through an intuitive interface.

.. toctree::
  :caption: User Guide
  :includehidden:
  :maxdepth: 3

  user_guide/ls1intum
  user_guide/workflows/pr_branch_selection
  user_guide/workflows/releasing
  user_guide/workflows/test_deployments
  user_guide/workflows/production_deployments
  user_guide/workflows/testing_analytics

.. toctree::
  :caption: Contributor Guide
  :includehidden:
  :maxdepth: 3

  contributor/architecture
  contributor/tech_stack
  contributor/setup
  contributor/start_app
  contributor/openapi
  contributor/webhook
  contributor/migrations
  contributor/keycloak_token_exchange
  contributor/testing

.. toctree::
  :caption: Admin Guide
  :includehidden:
  :maxdepth: 3

  admin/setup
  admin/troubleshooting

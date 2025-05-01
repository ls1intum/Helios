.. _artemis:

======================================================
Helios: Enhanced UI for GitHub Actions
======================================================

The personification of the sun, symbolizing light, clarity, and visibility that this app brings to CI/CD processes and test reliability

.. raw:: html

   <a href="_images/helios-icon-1280x640.png" target="_blank">
     <img src="_images/helios-icon-1280x640.png" alt="Helios the Sun God" style="height: 250px;" />
   </a>

Introduction
------------

The Artemis open-source project relies on GitHub Actions for its CI/CD workflows but faces challenges such as complex workflows, lack of user-friendly deployment management, and limited test analytics. This project aims to enhance the CI/CD capabilities of Artemis by developing a centralized web application that integrates with GitHub Actions. The proposed solution will provide a streamlined platform for managing deployments, environment tracking, and test analytics, accessible through an intuitive interface.

.. toctree::
  :caption: User Guide
  :includehidden:
  :maxdepth: 3

  user_guide/deployments/testserver

.. toctree::
  :caption: Contributor Guide
  :includehidden:
  :maxdepth: 3

  development/local/setup
  development/local/start_app
  development/local/openapi
  development/local/webhook
  development/local/migrations
  development/local/keycloak_token_exchange
  development/local/testing

.. toctree::
  :caption: Production
  :includehidden:
  :maxdepth: 3

  development/production/ls1intum
  development/production/setup

.. toctree::
  :caption: System
  :includehidden:
  :maxdepth: 3

  system/architecture
  system/tech_stack
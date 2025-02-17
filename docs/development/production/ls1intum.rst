===============================
Repository Integration Guide
===============================

How to Start Using Helios
-------------------------

1. **Define Your Environments**:

  - Navigate to your GitHub repository settings.
  - Open the "Environments" section.
  - Create your environments (e.g., `staging`, `production`, `test-server1`, `test-server2`, etc.).

2. **Configure Deployment Workflow**:

Create a GitHub Actions workflow (example ``deploy-with-helios.yml``) with ``workflow_dispatch`` trigger:

.. code-block:: yaml

   name: Deploy with Helios

  on:
    workflow_dispatch:
      inputs:
        # The inputs below must match exactly to work with Helios
        branch_name:
          description: "Which branch to deploy"
          required: true
          type: string
        environment_name:
          description: "Which environment to deploy (e.g. environment defined in GitHub)"
          required: true
          type: string
        triggered_by:
          description: "Username that triggered deployment (not required, shown if triggered via GitHub UI, logged if triggered via GitHub app)"
          required: false
          type: string

  # Suggestion: Ensures only one workflow runs at a time for a given environment name
  concurrency: ${{ github.event.inputs.environment_name }}

  jobs:
    build:
      runs-on: ubuntu-latest
        steps:
          - name: Checkout
            uses: actions/checkout@v4
            with:
              ref: ${{ github.event.inputs.branch_name }}
          - name: (Optional) Build or Prepare
            run:
              |
              echo "Run build steps or check for existing build here..."

    deploy:
      needs: [ build ]
      runs-on: ubuntu-latest
      # The "environment" keyword must be set at the job level. It should be set in the deploy job (most likely the last job in the workflow).
      environment: ${{ github.event.inputs.environment_name }}
      steps:
        - name: Checkout
          uses: actions/checkout@v4
          with:
            ref: ${{ github.event.inputs.branch_name }}

        # Add your deployment steps here

- **Multiple Jobs**: This example defines two jobs:

    - ``build`` - for building/checking your application or preparing assets.
    - ``deploy`` - for actually deploying to the GitHub environment.

- **Environment at the Job Level**:
  In GitHub Actions, you can only specify the ``environment`` keyword at the job level. This ensures that GitHub knows which environment the job is targeting.

- **Trigger Source**: Helios will trigger this workflow by sending a ``workflow_dispatch`` event, supplying the relevant metadata (``branch_name``, ``environment_name``, ``triggered_by``).

- **Concurrency**: Setting
  ``concurrency: ${{ github.event.inputs.environment_name }}``
  ensures only one deployment can run at a time for a given environment.

- **Helios as the Actor**: The ``workflow_dispatch`` event in **GitHub** can be triggered via the GitHub UI or API by anyone who has ``WRITE`` permissions to the repository in GitHub. This means that even if Helios is unresponsive, you can manually trigger deployments using the GitHub UI.


You can also find the workflow that is in use for Artemis repository `here <https://github.com/ls1intum/Artemis/blob/develop/.github/workflows/testserver-deployment.yml>`_.

3. **Install the GitHub App**:

  - Installation URL: https://github.com/apps/helios-aet
  - If you are a repository admin/maintainer and cannot install the app directly, please reach out to one of the organization admins and request the installation of this app into your repository.

4. **Verify Installation**:

  - Ensure the Helios app appears in your repository's installed apps:
    ``https://github.com/ls1intum/<REPO_NAME>/settings/installations``

5. **Configure Project Settings (Helios)**:

  - Access the Helios dashboard: https://helios.aet.cit.tum.de
  - Locate your repository in the repository list and select it
  - Navigate to project settings (Admin/maintainer access required)
  - Configure essential settings:

    - Set ``DEPLOYMENT`` label for your deployment workflow
    - Create workflow groups for logical grouping in PR/Branch views
    - Adjust default lock reservation/expiration times

6. **Configure Environment Settings (Helios)**:

   - In your repository's Helios dashboard:
   - Go to environments page
   - Edit an environment (Admin/maintainer access required)
   - Configure environment parameters:

     * Update server URL
     * Set up status checks:

       - HTTP Status Check (GET request with status validation)
       - Custom Status Check (GET request with body validation, e.g., ``https://artemis-test2.artemis.cit.tum.de/management/info``): This option requires the endpoint to return a specific output format for validation.
     * Override lock settings for specific environments

.. raw:: html

   <br>

7. **Trigger a Deployment**:

  - In your repository's Helios dashboard:
  - Go to the CI/CD page
  - Select a PR or branch
  - Click on the ``Deploy`` button


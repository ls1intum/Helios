====================
Setup Guide
====================

This guide focuses on setting up the Helios application on your local machine. The following sections will walk you through the necessary steps to install the required dependencies and run the application.
The production setup is covered in the `Production Setup Guide <production_setup.html>`_.


Prerequisites
-------------

Before you can start Helios, you need to install and configure some dependencies and tools.

1. `Java JDK <https://www.oracle.com/java/technologies/javase-downloads.html>`__:
   We use Java (JDK 21) to develop and run the 
   server, which is based on `Spring
   Boot <http://projects.spring.io/spring-boot>`__.

2. `Docker <https://docs.docker.com/get-docker/>`__:
   We use Docker to run the database, client and other services in
   containers. You can check if you installed Docker correctly by running
   ``docker --version`` in your terminal.

3. `Ngrok <https://ngrok.com/download>`__:
   (optional when not using webhook) We use Ngrok to expose the webhook listener to the internet. You can check if you installed Ngrok correctly by running
   ``ngrok --version`` in your terminal.

IDE Setup
---------

The first step is to set up an Integrated Development Environment (IDE) for development.

We recommend the lightweight `Visual Studio Code <https://code.visualstudio.com/>`__ (VS Code) editor.

Alternatively, you can use `IntelliJ IDEA <https://www.jetbrains.com/idea/download/>`__ (or other IDE's).

In case you use **VS Code**, install the recommended extensions when opening the project for the first time. Alternatively you can find them in ``.vscode/extension.json`` and install them manually via the extension manager in the menu on the left.

Clone the Repository
--------------------

Clone the Helios repository to your local machine:

.. code-block:: shell

   git clone git@github.com:ls1intum/Helios.git     # Clone with git
   git clone https://github.com/ls1intum/Helios.git # Alternatively clone with https

Application Configuration
-------------------------

For running the docker containers or the application server with the necessary configuration, you need to set up the environment variables.
The environment variables are stored in the `.env` file in the root directory of the project as well as in the application-server directory. 
You can find `.env.example` files with example data in the respective directories. Copy the `.env.example` file and rename it to `.env`. 
You can then change/set the environment variables in the `.env` file to your needs.

Following environment variables have to be set:


Webhook Listener Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
`(root .env file)`

- ``NATS_URL``: NATS server URL
- ``NATS_AUTH_TOKEN``: Authorization token for NATS server. This token is used to authenticate different services with the NATS server. You can generate any token you want.
- ``WEBHOOK_SECRET``: HMAC secret for verifying GitHub webhooks. This should be equal to the webhook secret you set in GitHub.

Postgres Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
`(root .env file)`

- ``POSTGRES_DB``: Database name
- ``POSTGRES_USER``: Database user
- ``POSTGRES_PASSWORD``: Database password

Be sure that the database information here matches those in the application server configuration.

Application Server Configuration
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
`(server/application-server/.env)`

You can configure **Helios** to work with **either** a Personal Access Token
(``GITHUB_AUTH_TOKEN``) **or** a GitHub App. Full functionality is only available
if you configure a GitHub App.

**If using a GitHub Personal Access Token**:

- ``GITHUB_AUTH_TOKEN``: Your personal access token (PAT).

**If using a GitHub App** (recommended for full functionality):

- ``GITHUB_APP_NAME``: Name of your GitHub App. After creation, you can confirm the "actual" URL-safe name in the GitHub UI or from the App URL in your browser. For example, if the GitHub UI shows "Helios (AET)" but the URL is ``https://github.com/organizations/ls1intum/settings/apps/helios-aet``, then ``GITHUB_APP_NAME=helios-aet``.
- ``GITHUB_APP_ID``: Numeric ID of your GitHub App (from GitHub App settings).
- ``GITHUB_CLIENT_ID``: Client ID (from GitHub App settings).
- ``GITHUB_PRIVATE_KEY_PATH``: **Absolute** or **relative** path to the **PKCS#8**-formatted private key file associated with your GitHub App. (See note below on converting from PKCS#1 to PKCS#8.)
- ``ORGANIZATION_NAME``: Name of the GitHub organization/user **only if** you do **not** know the installation ID and want Helios to **auto-detect** it at runtime. Helios will retrieve the GitHub App installations for the given organization/user name and pick the correct installation.
- ``GITHUB_INSTALLATION_ID``: Installation ID for your GitHub App. If you already know the ID (from the "Install App" screen), you can specify it here. In that case, ``ORGANIZATION_NAME`` is not used.

**Important**:
When Helios starts, it will look for all the **GitHub App** variables above:

- If **all** GitHub App variables (``GITHUB_APP_NAME``, ``GITHUB_APP_ID``,
  ``GITHUB_CLIENT_ID``, etc.) are present, Helios authenticates using the GitHub
  App credentials (full functionality).
- If they are **not** present, but ``GITHUB_AUTH_TOKEN`` exists, Helios falls back
  to using the **PAT**.

---

**Other Required Variables** (`server/application-server/.env`):

- ``DATASOURCE_URL``: URL to the database
- ``DATASOURCE_USERNAME``: Database username
- ``DATASOURCE_PASSWORD``: Database password
- ``NATS_SERVER``: NATS server URL
- ``NATS_AUTH_TOKEN``: Authorization token for NATS server. This token is used to authenticate different services with the NATS server.
- ``NATS_DURABLE_CONSUMER_NAME``: Name of the durable consumer for NATS server. With durable consumers, NATS remembers where it left off when the last event was acknowledged.
- ``NATS_CONSUMER_INACTIVE_THRESHOLD_MINUTES``: (Optional, default: 30) Specifies the time (in minutes) after which an inactive consumer is removed.
- ``NATS_CONSUMER_ACK_WAIT_SECONDS``: (Optional, default: 60) Specifies the time (in seconds) that NATS waits for a message acknowledgment before resending the message.
- ``REPOSITORY_NAME``: Name of the repository that should be synced (e.g. `ls1intum/Helios`)
- ``DATA_SYNC_RUN_ON_STARTUP``: Whether to run the data sync on startup (default: `true`)
- ``RUN_ON_STARTUP_COOLDOWN``: When server starts, it first checks the latest run of sync, if it is less than this value in minutes, it will not run the sync again
- ``OAUTH_ISSUER_URL``: URL to Keycloak realm
- ``NOTIFICATIONS_ENABLED``: (Optional, default: `true`) Whether to enable notifications to users

Creating a GitHub App
^^^^^^^^^^^^^^^^^^^^^
Below are typical steps and recommended settings:



1. **Go to GitHub "Developer settings"**

- If you want the app under an organization, go to:
     ``https://github.com/organizations/<ORG-NAME>/settings/apps``,
     then click **New GitHub App**.

    - If you want it under your personal account, go to: ``https://github.com/settings/apps``, then click **New GitHub App**.

2. **Provide Basic App Details**

    - **App name**: e.g. `my-helios` (`GITHUB_APP_NAME`).
    - **Homepage URL**: Can be your local dev URL or your production URL. (Optional)
    - **Enable Device Flow**: (Optional, depending on your needs.)
    - **Enable Webhooks**: Enable
        - **Webhook URL**: e.g. `https://<your-domain>/github` or `https://<ngrok-url>/github` (See the details about how to setup ngrok: `Setting Up ngrok Locally <webhook.html#setting-up-ngrok-locally>`_).
        - **Webhook Secret**: Must match your `WEBHOOK_SECRET` in `server/application-server/.env`

3. **Set Permissions**
   *(Minimal permissions for Helios.)*

    - **Repository Permissions**:
        - Actions: read & write
        - Commit statuses: read & write
        - Contents: read-only
        - Deployments: read & write
        - Environments: read-only
        - Issues: read-only
        - Metadata: read-only
        - Pull requests: read-only
    - **Organization/Account Permissions**:
        - Email addresses: read-only
    - **Subscribe to events**:
        - Create
        - Delete
        - Deployment
        - Deployment protection rule
        - Deployment Status
        - Issues
        - Label
        - Pull request
        - Push
        - Repository
        - Workflow dispatch
        - Workflow job
        - Workflow run

4. **Generate the Private Key**

   - After creating the app, generate a **private key** (`.pem` file). By default,
     GitHub provides a **PKCS#1**-formatted key.
   - Convert the `.pem` from PKCS#1 to **PKCS#8** (required by Helios):

    ``openssl pkcs8 -topk8 -nocrypt -in original_key.pem -out converted_key_pkcs8.pem``

   - Save this `converted_key_pkcs8.pem` in a secure location. Then set
     ``GITHUB_PRIVATE_KEY_PATH=/path/to/converted_key_pkcs8.pem`` in your `server/application-server/.env`.

5. **Install the GitHub App**

   - In your newly created App settings, click **Install App**.
   - Select the repositories you want Helios to manage, or "All repositories" if
     appropriate.
   - After installation, you will see an **Installation ID** (e.g. `12345678`) in the URL.
      - If you do **not** know the Installation ID, you can provide ``ORGANIZATION_NAME=<org-name>`` in your `server/application-server/.env`, and Helios will look up the correct installation ID at runtime.
      - Otherwise, set ``GITHUB_INSTALLATION_ID=12345678`` to skip the dynamic look-up.

6. **Collect and Set App Variables**

   - **App ID** (numeric, e.g. `987654`)
   - **Client ID** (e.g. `Iv1.XXXXXX...`)
   - **Installation ID** (if known)
   - **App Name** (actual slug, as seen in the browser URL, e.g. `my-helios`)
   - **Private Key Path** (the PKCS#8 `.pem` file you just generated)

.. note::
   For simple local testing, you **can skip** creating a GitHub App and just set
   a `GITHUB_AUTH_TOKEN`.


Example Deployment Workflow
---------------------------
To enable Helios to trigger deployments, your repository must have a corresponding GitHub Actions workflow that Helios can dispatch. Below is an example workflow file (`deploy-with-helios.yml`), which uses the ``workflow_dispatch`` event with specific input parameters.

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

Explanation
~~~~~~~~~~~~~~~~~~~~~~
- **Multiple Jobs**: This example defines two jobs:

  1. ``build`` - for building/checking your application or preparing assets.
  2. ``deploy`` - for actually deploying to the GitHub environment.

- **Environment at the Job Level**:
  In GitHub Actions, you can only specify the ``environment`` keyword at the job level. This ensures that GitHub knows which environment the job is targeting.

- **Trigger Source**: Helios will trigger this workflow by sending a ``workflow_dispatch`` event, supplying the relevant metadata (``branch_name``, ``environment_name``, ``triggered_by``).

- **Concurrency**: Setting
  ``concurrency: ${{ github.event.inputs.environment_name }}``
  ensures only one deployment can run at a time for a given environment.

- **Helios as the Actor**: The ``workflow_dispatch`` event in **GitHub** can be triggered via the GitHub UI or API by anyone who has ``WRITE`` permissions to the repository in GitHub. This means that even if Helios is unresponsive, you can manually trigger deployments using the GitHub UI.

By structuring your workflow like this, you can ensure that deployments can be triggered directly from the GitHub UI, providing flexibility and control over deployments.

Keycloak Setup
----------------

Repository should contain ``helios-example-realm.json`` file for local development. If the file is not present or you want to set up a fresh Keycloak realm, you can follow the below pages to set up Keycloak:

.. toctree::
   :maxdepth: 2
   :caption: Keycloak Configuration

   keycloak
   keycloak_token_exchange

Now you can continue running the application by following the steps in the `Starting the Application Guide <start_app.html>`_.

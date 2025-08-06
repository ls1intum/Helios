====================
Setup Guide
====================

This guide focuses on setting up the Helios application on your local machine. The following sections will walk you through the necessary steps to install the required dependencies and run the application.
The production setup is covered in the `Production Setup Guide <../admin/setup.html>`_.


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
   We use Ngrok to expose the webhook listener to the internet. You can check if you installed Ngrok correctly by running
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

.. note::

   Before running Helios, you need to set up ngrok and a GitHub App to receive events from GitHub.
   First step is to set up ngrok, which will expose your local webhook listener to the internet.
   Then, you need to create a GitHub App that Helios will use to authenticate and receive events from GitHub.

Setting Up ngrok Locally
--------------------------

Helios listens for events from GitHub repositories and publishes them to a NATS server.
This is essential for real-time event processing and integration with GitHub.
Events will be published to NATS with the subject:
``github.<owner>.<repo>.<event_type>``

1. **Install ngrok**
   You have two options:

   - Via npm::

         npm install -g ngrok

   - Download the binary directly from https://ngrok.com/downloads/
     and follow the platform-specific instructions.

2. **Create an ngrok Account & Obtain Your Authtoken**
   - Go to https://ngrok.com/ and sign up (or log in).
   - From the navigation bar, select “Getting Started → Your Authtoken.”
   - Copy the Authtoken string. You’ll add this to your local ``ngrok.yml`` configuration.

3. **Reserve a Persistent Domain**
   By default, free ngrok tunnels use a random subdomain each time you start them. To avoid updating your GitHub webhook URL on every restart, reserve one persistent domain:

   - In the ngrok dashboard, navigate to **Universal Gateway → Domains**.
   - Click **New Domain** and follow the prompts to acquire a free persistent domain (e.g., ``<YOUR_PERSISTENT_DOMAIN>.ngrok-free.app``).
   - Note down the domain name you choose, as you will need it later.

4. **Configure ngrok**
   By default, ngrok looks for a config file at:

 - macOS: ``~/Library/Application Support/ngrok/ngrok.yml``
 - Windows: ``C:\Users\<YourUsername>\.ngrok2\ngrok.yml``

   Your ``ngrok.yml`` should include at least::

       version: 3

       agent:
         authtoken: <YOUR_AUTHTOKEN>

       endpoints:
         - name: webhook
           url: <YOUR_PERSISTENT_DOMAIN>.ngrok-free.app
           upstream:
             url: 4201

   - ``authtoken``: Paste the token you copied from ngrok’s dashboard.
   - ``name``: A label for this tunnel (e.g., ``webhook`` this name will be later used to run the ngrok).
   - ``url``: Your persistent domain (explained above).
   - ``upstream.url``: The local port where your webhook listener is running (for local development it is ``4201``).

5. **Verify Your Configuration**
   Run the following command::

       ngrok config check

   If everything is set up correctly, you should see something like::

       Valid configuration file at /Users/you/Library/Application Support/ngrok/ngrok.yml

6. **Run ngrok**
   With your configuration in place, start ngrok::

       ngrok start webhook

   This reads the ``webhook`` entry in ``ngrok.yml``, creates a tunnel, and prints the public URL. Point your GitHub webhook to::

       https://<YOUR_PERSISTENT_DOMAIN>.ngrok-free.app

Now, whenever GitHub sends an event to that URL, ngrok forwards it to your local service on port 4201. The listener picks it up and publishes it to NATS under the subject ``github.<owner>.<repo>.<event_type>``.


Creating a GitHub App
-------------------------

Helios can be configured to use either a Personal Access Token (PAT) or a GitHub App for authentication and authorization with GitHub.
Using a GitHub App is recommended for full functionality.
We recommend you to fist create an organization on GitHub and then create a GitHub App under that organization.
This allows you to separate your testing environment.
Below are the steps to create a GitHub App:

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
        - **Webhook URL**: e.g. ``https://<your-domain>/github`` or ``https://<ngrok-url>/github`` (See the details about how to setup ngrok: `Setting Up ngrok Locally <setup.html#setting-up-ngrok-locally>`_).
        - **Webhook Secret**: Must match your `WEBHOOK_SECRET` in root `.env` file. Please generate a secure secret and store it for later use.

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
     ``GITHUB_PRIVATE_KEY_PATH=/path/to/converted_key_pkcs8.pem`` in your ``server/application-server/.env``. For local development, please save it under ``server/application-server/`` folder and you can use a relative path like ``./converted_key_pkcs8.pem`` later on when we set the environment variables in the next steps.

5. **Install the GitHub App**

   - In your newly created App settings, click **Install App**.
   - Select the repositories you want Helios to manage, or "All repositories" if
     appropriate.

6. **Save information for later use in environment variables**

   - **App ID** (numeric, e.g. `987654`)
   - **Client ID** (e.g. `Iv1.XXXXXX...`)
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

Application Configuration
-------------------------

For running the docker containers or the application server with the necessary configuration, you need to set up the environment variables.
The environment variables are stored in the ``.env`` file in the root directory of the project as well as in the **application-server** directory.
You can find ``.env.example`` files with example data in the respective directories. Copy the ``.env.example`` files and rename them to ``.env``.
You can then change/set the environment variables in the ``.env`` files to your needs.

Below you can find the required environment variables. For the default values, refer to the
``.env.example`` files in the respective directories:

- Root: `.env.example (root)`_
- Application Server: `.env.example (application-server)`_

.. _`.env.example (root)`: https://github.com/ls1intum/Helios/blob/staging/.env.example
.. _`.env.example (application-server)`: https://github.com/ls1intum/Helios/blob/staging/server/application-server/.env.example



root ``.env`` file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- ``POSTGRES_DB``: Database name (Used by ``postgres`` container)
- ``POSTGRES_USER``: Database user (Used by ``postgres`` and ``keycloak`` container)
- ``POSTGRES_PASSWORD``: Database password (Used by ``postgres`` and ``keycloak`` container)
- ``NATS_SERVER``: NATS server URL (Used by ``notification`` container)
- ``NATS_AUTH_TOKEN``: This token is used to authenticate different services with the NATS server. You can generate any token you want. (Used by ``nats-server``, ``webhook-listener`` and ``notification`` container)
- ``WEBHOOK_SECRET``: HMAC secret for verifying GitHub webhooks. This should be equal to the webhook secret you set in GitHub App.
- ``KC_HOSTNAME``: Hostname of Keycloak (Used by ``keycloak`` container)
- ``KEYCLOAK_ADMIN``: Keycloak admin username. This is used to create the initial admin user in Keycloak. (Used by ``keycloak`` container)
- ``KEYCLOAK_ADMIN_PASSWORD``: Keycloak admin password. This is used to create the initial admin user in Keycloak. (Used by ``keycloak`` container)

``server/application-server/.env`` file
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- ``SPRING_PROFILES_ACTIVE``: Active Spring profile. For local development, you can set this to `dev`
- ``DATASOURCE_URL``: URL to the database. This should be in the format ``jdbc:postgresql://<db-host>/<db-name>``
- ``DATASOURCE_USERNAME``: Database username. This should match the ``POSTGRES_USER`` used in the root ``.env`` file.
- ``DATASOURCE_PASSWORD``: Database password. This should match the ``POSTGRES_PASSWORD`` used in the root ``.env`` file.
- ``NATS_AUTH_TOKEN``: Authorization token for NATS server. This should match the ``NATS_AUTH_TOKEN`` used in the root ``.env`` file.
- ``NATS_DURABLE_CONSUMER_NAME``: Name of the durable consumer for NATS server. With durable consumers, NATS remembers where it left off when the last event was acknowledged. On startup, if a durable consumer with this name already exists, it will be used. Otherwise, a new one will be created.
- ``NATS_CONSUMER_INACTIVE_THRESHOLD_MINUTES``: Durable consumer specific settings
- ``NATS_CONSUMER_ACK_WAIT_SECONDS``: Durable consumer specific settings
- ``REPOSITORY_NAME``: Set only if you want to sync a specific repository. This should be in the format ``owner/repo`` (e.g. ``ls1intum/Helios,ls1intum/Artemis``). Regardless of this value, Helios will sync all repositories that the GitHub App has installed to. If GitHub App credentials are not used and only a Personal Access Token (PAT) is used, only this repository will be synced.
- ``DATA_SYNC_RUN_ON_STARTUP``: Whether to run the data sync on startup.
- ``RUN_ON_STARTUP_COOLDOWN``: When server starts, it first checks the latest run of sync, if it is less than this value in minutes, it will not run the sync again.
- ``OAUTH_ISSUER_URL``: URL to Keycloak realm in the format ``http://<keycloak-host>:<keycloak-port>/realms/<realm-name>``
- ``HELIOS_TOKEN_EXCHANGE_CLIENT``: Client ID for the token exchange client in Keycloak. This is used to exchange the access token for a user token. With this Helios is able to get the logged in user's GitHub token and use it to perform actions on behalf of the user.
- ``HELIOS_TOKEN_EXCHANGE_SECRET``: Client secret for the token exchange client in Keycloak. This is used to exchange the access token for a user token. With this Helios is able to get the logged in user's GitHub token and use it to perform actions on behalf of the user.
- ``NOTIFICATIONS_ENABLED``: (Optional, default: `true`) Whether to enable notifications to users

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


Keycloak Setup
----------------

Repository should contain ``helios-example-realm.json`` file for local development. If the file is not present or you want to set up a fresh Keycloak realm, you can follow the below pages to set up Keycloak:

.. toctree::
   :maxdepth: 2
   :caption: Keycloak Configuration

   keycloak
   keycloak_token_exchange

Now you can continue running the application by following the steps in the `Starting the Application Guide <start_app.html>`_.

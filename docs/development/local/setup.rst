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
~~~~~~~~~~~~
`(root .env file)`

- ``NATS_URL``: NATS server URL
- ``NATS_AUTH_TOKEN``: Authorization token for NATS server. This token is used to authenticate different services with the NATS server. You can generate any token you want.
- ``WEBHOOK_SECRET``: HMAC secret for verifying GitHub webhooks. This should be equal to the webhook secret you set in GitHub.

Postgres Configuration
~~~~~~~~~~~~
`(root .env file)`

- ``POSTGRES_DB``: Database name
- ``POSTGRES_USER``: Database user
- ``POSTGRES_PASSWORD``: Database password

Be sure that the database information here matches those in the application server configuration.

Application Server Configuration
~~~~~~~~~~~~
`(server .env file)`

You can configure **Helios** to work with **either** a Personal Access Token
(``GITHUB_AUTH_TOKEN``) **or** a GitHub App. **Full functionality** (including
Helios's custom deployment protection rules) is only available if you configure
a GitHub App.

**If using a GitHub Personal Access Token**:

- ``GITHUB_AUTH_TOKEN``: Your personal access token (PAT). *(This enables basic GitHub operations but not custom deployment protection rules.)*

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
  to using the **PAT** (partial functionality, no custom deployment protection rules).

---

**Other Required Variables** (server `.env`):

- ``DATASOURCE_URL``: URL to the database
- ``DATASOURCE_USERNAME``: Database username
- ``DATASOURCE_PASSWORD``: Database password
- ``NATS_SERVER``: NATS server URL
- ``NATS_AUTH_TOKEN``: Authorization token for NATS server. This token is used to authenticate different services with the NATS server.
- ``REPOSITORY_NAME``: Name of the repository that should be synced (e.g. `ls1intum/Helios`)
- ``RUN_ON_STARTUP_COOLDOWN``: When server starts, it first checks the latest run of sync, if it is less than this value in minutes, it will not run the sync again
- ``OAUTH_ISSUER_URL``: URL to Keycloak realm

.. note::
   The above variables must be defined in the `.env` file located in
   `server/application-server`, as indicated in the Setup Guide.


Creating a GitHub App
^^^^^^^^^^^^^^^^^^^^^
In order to use Helios’s **custom deployment protection rules** and other advanced
features, you must create a GitHub App (either under an organization or under a
personal user). Below are typical steps and recommended settings:



1. **Go to GitHub “Developer settings”**

- If you want the app under an organization, go to:
     ``https://github.com/organizations/<ORG-NAME>/settings/apps``,
     then click **New GitHub App**.

    - If you want it under your personal account, go to:
     ``https://github.com/settings/apps``,
     then click **New GitHub App**.

2. **Provide Basic App Details**

    - **App name**: e.g. `my-helios` (`GITHUB_APP_NAME`).
    - **Homepage URL**: Can be your local dev URL or your production URL. (Optional)
    - **Enable Device Flow**: (Optional, depending on your needs.)
    - **Enable Webhooks**: Enable
        - **Webhook URL**: e.g. `https://<your-domain>/github` or `http://<ngrok-url>/github` (if ngrok is running and forwarding to port 4201 for local development).
        - **Webhook Secret**: Must match your `WEBHOOK_SECRET` in `.env`

3. **Set Permissions**
   *(Minimal permissions for Helios.)*

    - **Repository Permissions**:
        - Actions: read & write
        - Contents: read-only
        - Deployments: read & write
        - Environments: read-only
        - Issues: read-only
        - Metadata: read-only
        - Pull requests: read-only
    - **Organization/Account Permissions**:
        - Email addresses: read-only
    - **Subscribe to events**:
        - Create, Delete, Deployment, Deployment protection rule, Deployment Status,
        Issues, Label, Pull request, Push, Repository, workflow dispatch,
        workflow job, workflow run

4. **Generate the Private Key**

   - After creating the app, generate a **private key** (`.pem` file). By default,
     GitHub provides a **PKCS#1**-formatted key.
   - Convert the `.pem` from PKCS#1 to **PKCS#8** (required by Helios):

    ``openssl pkcs8 -topk8 -nocrypt -in original_key.pem -out converted_key_pkcs8.pem``

   - Save this `converted_key_pkcs8.pem` in a secure location. Then set
     ``GITHUB_PRIVATE_KEY_PATH=/path/to/converted_key_pkcs8.pem`` in your `.env`.

5. **Install the GitHub App**

   - In your newly created App settings, click **Install App**.
   - Select the repositories you want Helios to manage, or "All repositories" if
     appropriate.
   - After installation, you will see an **Installation ID** (e.g. `12345678`).
      - If you do **not** know the Installation ID, you can provide ``ORGANIZATION_NAME=<org-name>`` in your `.env`, and Helios will look up the correct installation ID at runtime.
      - Otherwise, set ``GITHUB_INSTALLATION_ID=12345678`` to skip the dynamic look-up.

6. **Collect and Set App Variables**

   - **App ID** (numeric, e.g. `987654`)
   - **Client ID** (e.g. `Iv1.XXXXXX...`)
   - **Installation ID** (if known)
   - **App Name** (actual slug, as seen in the browser URL, e.g. `my-helios`)
   - **Private Key Path** (the PKCS#8 `.pem` file you just generated)

Insert these values into your `.env` in the `server/application-server` directory.

.. note::
   For simple local testing, you **can skip** creating a GitHub App and just set
   a `GITHUB_AUTH_TOKEN`. However, **custom deployment protection rules** will
   **not** work with a PAT alone.


Now you can continue running the application by following the steps in the `Starting the Application Guide <start_app.html>`_.

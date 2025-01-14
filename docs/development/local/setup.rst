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

**Webhook Listener Configuration** (root .env file):

- `NATS_URL`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `WEBHOOK_SECRET`: HMAC secret for verifying GitHub webhooks. This should be equal to the webhook secret you set in GitHub.

**Postgres Configuration** (root .env file):

- `POSTGRES_DB`: Database name
- `POSTGRES_USER`: Database user
- `POSTGRES_PASSWORD`: Database password

Be sure that the database information here matches those in the application server configuration.

**Application Server Configuration** (server .env file):

- `DATASOURCE_URL`: URL to the database
- `DATASOURCE_USERNAME`: Database username
- `DATASOURCE_PASSWORD`: Database password
- `NATS_SERVER`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `REPOSITORY_NAME`: Name of the repository that should be synced
- `ORGANIZATION_NAME`: Name of the organization/user of the repository that should be synced
- `GITHUB_AUTH_TOKEN`: GitHub personal access token
- `RUN_ON_STARTUP_COOLDOWN`: When server starts, it first checks the latest run of sync, if it is less than this value in minutes, it will not run the sync again
- `OAUTH_ISSUER_URL`: URL to Keycloak realm

Now you can continue running the application by following the steps in the `Starting the Application Guide <start_app.html>`_.

=====================
Keycloak Setup
=====================

This guide shows how to run Keycloak locally (via Docker Compose), create a new realm from scratch (or skip straight to configuration if you already have one), configure GitHub as an identity provider, create application and clients, and finally export your realm to JSON for reuse.

Prerequisites
-------------

* A GitHub account with permission to create a GitHub OAuth App (or a GitHub App).
* Docker and Docker Compose installed locally.

Environment Variables
-------------------------

.. code-block:: bash

   # PostgreSQL
   POSTGRES_DB=helios
   POSTGRES_USER=helios
   POSTGRES_PASSWORD=helios

   # Keycloak Admin Account
   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=admin
   KC_HOSTNAME=localhost


Docker Compose Configuration
----------------------------

.. code-block:: yaml

   version: '3.9'

   services:
     postgres:
       image: 'postgres:16.10-alpine'
       environment:
         - POSTGRES_DB=${POSTGRES_DB}
         - POSTGRES_USER=${POSTGRES_USER}
         - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
       ports:
         - '5432:5432'
       volumes:
         - db-data:/var/lib/postgresql/data
       command: >
         bash -c "
         set -m
         docker-entrypoint.sh postgres &
         until pg_isready -h localhost -p 5432 --timeout=0; do
            echo 'Waiting for Postgres to be ready...';
            sleep 1;
         done;
         psql -v ON_ERROR_STOP=1 -U ${POSTGRES_USER} -tc \"SELECT 1 FROM pg_database WHERE datname = 'keycloak'\" \
            | grep -q 1 || psql -U ${POSTGRES_USER} -c \"CREATE DATABASE keycloak;\"
         fg %1
         "
       networks:
         - helios-network

     keycloak:
       image: quay.io/keycloak/keycloak:26.1.3
       container_name: keycloak
       environment:
         KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN}
         KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
         KC_DB: postgres
         KC_DB_URL_HOST: postgres
         KC_DB_USERNAME: ${POSTGRES_USER}
         KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
         KC_HOSTNAME: ${KC_HOSTNAME}
       ports:
         - '8081:8081'
       depends_on:
         - postgres
       # If you have an existing realm JSON, mount it here; otherwise skip the volume and import flags
       # volumes:
       #   - ./helios-example-realm.json:/opt/keycloak/data/import/helios-example-realm.json:ro
       command: start-dev --http-port=8081 --features="token-exchange,admin-fine-grained-authz"
       networks:
         - helios-network

   volumes:
     db-data:

   networks:
     helios-network:
       name: helios-network

Setup Steps
-----------

1. Start Docker Compose
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

From the directory containing docker-compose.yml and .env, run:

.. code-block:: bash

   docker compose up --build

#. PostgreSQL will initialize and create the keycloak database.
#. Keycloak (in dev mode) will start on port 8081.
#. Wait until logs show:

.. code-block:: bash

   Started Keycloak in development mode (boot time: X seconds)

2. Open the Keycloak Admin Console
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Access the Keycloak Admin Console at ``http://localhost:8081/admin``
#. Log in with the admin credentials you set in ``.env``
#. Create a new realm from: ``Manage Realms`` -> ``Create Realm``

.. warning::

   If you already have a realm, you can skip from here and continue from step 7.

.. raw:: html

   <a href="../../_static/images/keycloak/create-realm.png" target="_blank">
     <img src="../../_static/images/keycloak/create-realm.png" alt="Create Realm" style="height: 512px;" />
   </a>  


3. Connecting Keycloak to GitHub App
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Go back to the Github App created in prevous section (Setup Guide)

- Add a callback URL:

  - Local development: ``http://localhost:4200/realms/helios-test/broker/github/endpoint``
  - Production: ``https://yourdomain.com/realms/helios-test/broker/github/endpoint`` (replace with your actual domain, such as ``helios.aet.cit.tum.de``)

- Go to you GitHub App that we created in `Creating a GitHub App <setup.html#creating-a-github-app>`_ and create and note down the **Client ID** and **Client Secret**.


4. Add GitHub as an Identity Provider in Keycloak
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Go to ``Identity Providers``
2. Choose ``GitHub``
3. Add Display Name: ``GitHub``
4. Add the **Client ID** and **Client Secret** you noted down in the previous step.

.. raw:: html

   <a href="../../_static/images/keycloak/add-github-idp.png" target="_blank">
     <img src="../../_static/images/keycloak/add-github-idp.png" alt="Add GitHub as an Identity Provider" style="height: 512px;" />
   </a>

5. Scopes: ``read:user`` workflow
6. Enable **Store Tokens** (so Keycloak retains the GitHub access token).
7. Click ``Save``
8. Add a Protocol Mapper:

  - With this mapper, the generated Keycloak JWT will include the GitHub user ID as a user attribute, which can be used to link Keycloak users with their GitHub accounts in our ``application-server``.

.. raw:: html

   <a href="../../_static/images/keycloak/add-github-idp-mapper.png" target="_blank">
     <img src="../../_static/images/keycloak/add-github-idp-mapper.png" alt="Add GitHub as an Identity Provider" style="height: 512px;" />
   </a>



5. Creating the Client
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Go to ``Clients`` -> ``Create Client``
#. Add Client ID: ``helios-example``
#. Add Root URL: ``http://localhost:4200`` (or your domain)
#. Add Valid Redirect URIs: ``http://localhost:4200/*`` (or your domain)
#. Add Valid Post Logout Redirect URIs: ``http://localhost:4200/*`` (or your domain)
#. Add Web Origins: ``*``
#. Add Admin URL: ``http://localhost:4200`` (or your domain)


6. Adding Mappers to the Client
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Go to the ``Clients`` -> previously created client (``helios-example``)
#. Go to the ``Client Scopes``
#. Select the relevant scope
#. Add Mapper -> By configuration -> ``User Attribute``
#. Add following: 

.. raw:: html

   <a href="../../_static/images/keycloak/add-mapper.png" target="_blank">
     <img src="../../_static/images/keycloak/add-mapper.png" alt="Add Mapper" style="height: 512px;" />
   </a>


7. Exporting the realm
~~~~~~~~~~~~~~~~~~~~~~

1. Go to ``Realm Settings`` -> ``Actions`` -> ``Partial Export``:

.. raw:: html

   <a href="../../_static/images/keycloak/export-realm.png" target="_blank">
     <img src="../../_static/images/keycloak/export-realm.png" alt="Export Realm" style="height: 512px;" />
   </a>

2. Click ``Export``

Now you can use the exported ``realm.json`` file to import it into your Keycloak instance.
Uncomment the volume in the ``docker-compose.yml`` file and mount the ``realm.json`` file to the container.



Custom Keycloak Image (Helios Theme)
------------------------------------

We build Keycloak from our own ``Dockerfile`` under ``keycloakify/`` directory in our monorepo, which runs keycloakify to compile a React‐based custom “Helios” login theme and places it into ``/opt/keycloak/providers/``. In ``docker-compose.yml``, the keycloak service is defined with ``build: context: ./keycloakify``, and we set ``KC_THEME=helios-login`` so that Keycloak serves our branded “Helios” login page instead of the default.



Troubleshooting
---------------

If you encounter issues:

**1. Postgres not ready**:

- Compose's ``pg_isready`` loop retries indefinitely. Check logs:

.. code-block:: bash

   docker-compose logs -f postgres

**2. Admin login fails**:

- Ensure ``KEYCLOAK_ADMIN`` / ``KEYCLOAK_ADMIN_PASSWORD`` match both ``.env`` and your login attempt.
- To reset, delete the Keycloak volume and restart.

**3. GitHub “Redirect URI mismatch”**:

- In Github App settings, verify the callback URL matches the one in Keycloak.
- In Keycloak’s ``Identity Providers`` → ``GitHub`` → ``Settings``, confirm **Client ID** and **Client Secret**.

**4. Realm import errors**:   

- Check that the mounted JSON is valid and readable under ``/opt/keycloak/data/import/``.
- View errors in Keycloak logs.




For additional information, refer to the `Keycloak documentation <https://www.keycloak.org/documentation>`_.

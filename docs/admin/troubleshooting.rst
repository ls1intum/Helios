========================
Troubleshooting Helios
========================


.. contents:: Content of this document
    :local:
    :depth: 2


Updating SSL/TLS Certificates
-------------------------------

.. note::

  The Helios Docker Compose file includes all services (``application-server``, ``webhook-listener``, ``client``, ``database``, ``NATS``, etc.) except ``nginx``. Nginx run as a separate container and attached to the same network (``helios-network``)

  .. code-block:: console

    docker run -d \
      --name nginx \
      --restart unless-stopped \
      -p 80:80 -p 443:443 \
      -v /etc/nginx/conf/nginx.conf:/etc/nginx/nginx.conf:ro \
      -v /etc/nginx/certs:/etc/nginx/certs:ro \
      --net helios-network \
      nginx:latest

  Once Nginx is running on the ``helios-network``, it will proxy traffic to the Helios services defined in Docker Compose.

When your Let’s Encrypt certificates approach expiration (every 90 days), follow these steps to renew and apply them.

1. **Renew Certificates**

   Use Certbot in standalone mode to obtain or renew certificates for your Helios domain. Replace <your-domain> with your actual hostname (e.g., `helios.aet.cit.tum.de`):

   .. code-block:: console

      sudo certbot certonly --standalone -d <your-domain>


  This will generate or renew the certificate files under:
  `/etc/letsencrypt/live/<your-domain>/fullchain.pem`
  `/etc/letsencrypt/live/<your-domain>/privkey.pem`

2. **Verify Nginx Configuration**

Ensure your Nginx configuration references the correct certificate paths. Open the live Nginx config (not the repository copy) at `/etc/nginx/conf/nginx.conf` and confirm lines similar to:

    .. code-block:: nginx

        server {
            listen 443 ssl;
            server_name <your-domain>;

            ssl_certificate     /etc/letsencrypt/live/<your-domain>/fullchain.pem;
            ssl_certificate_key /etc/letsencrypt/live/<your-domain>/privkey.pem;

            # …other configuration…
        }

3. **Reload or Restart Nginx Container**

After certificates are in place and the config is correct, reload the Nginx container so it picks up the new files:

    .. code-block:: console

        docker restart nginx

    This command restarts the Nginx container, applying the new SSL/TLS certificates.

NATS Webhook Data Cleanup
-------------------------------

Helios uses NATS to buffer incoming webhook events. These messages are stored persistently in a Docker volume named ``helios_nats-data``. Over time, old webhook data may accumulate and can be safely removed if it is no longer needed.

1. **List Docker Volumes**

   To see the NATS data volume, run:

   .. code-block:: console

      docker volume ls

   Look for a volume named `helios_nats-data`.

2. **Stop the Helios**

   Before deleting the NATS data volume, stop the entire Helios stack to avoid data corruption:

   .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml down --remove-orphans --rmi all

3. **Remove the NATS Data Volume**

   To delete the NATS data volume, run:

   .. code-block:: console

      docker volume rm helios_nats-data

   This command will remove the persistent storage for NATS, effectively clearing all buffered webhook data.

4. **Restart Helios**

   After cleaning up the NATS data, you can restart the Helios stack:

   .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml --env-file=.env up --pull=always -d

If Helios Stops Processing Webhook Events
-------------------------------------------

In some cases, the Helios ``application-server`` may stop handling incoming GitHub webhook events. This is often caused by the NATS consumer being deleted due to inactivity. Below is a brief overview of the issue, initial diagnostic steps, and remediation.

Issue Overview
~~~~~~~~~~~~~~~~~~~~~~~

- The application-server subscribes to a NATS stream (“github”) to receive webhook messages produced by the webhook-listener.
- If the NATS consumer is inactive (does not pull new messages or acknowledge an event) for a certain period, NATS may delete the consumer automatically.
- When the consumer is deleted, you will see errors such as:

  .. code-block:: console

      application-server-1  | 2025-02-07T15:51:06.956Z ERROR 1 --- [Helios] [pool-3-thread-1] i.n.client.impl.ErrorListenerLoggerImpl  : pullStatusError, Connection: 13, Subscription: 615556999, Consumer Name: xy23djLX, Status:Status{code=409, message='Consumer Deleted'}
      application-server-1  | 2025-02-07T15:53:59.905Z ERROR 1 --- [Helios] [pool-3-thread-1] i.n.client.impl.ErrorListenerLoggerImpl  : heartbeatAlarm, Connection: 13, Subscription: 94524147, Consumer Name: xy23djLX, lastStreamSequence: 58810, lastConsumerSequence: 40771

- After this error, no new webhook events will be processed until the consumer is re-created.

Initial Diagnostic Steps
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. **Verify GitHub App Webhooks**

   Go to the GitHub App settings page: https://github.com/organizations/ls1intum/settings/apps/helios-aet/advanced

   .. code-block:: console

      docker logs -f application-server

  - Under ``Advanced`` check that webhooks are being delivered successfully.
  - If webhook deliveries are failing, inspect the HTTP response codes and payloads.

    - Failed deliveries may indicate issues with the ``webhook-listener``.

2. **Check Logs for Event Receipt**

  - **webhook-listener Logs**: Confirm that the ``webhook-listener`` container is receiving GitHub events. You should see log entries like below:

    .. code-block:: console

          INFO:	  Published message to github.ls1intum.Artemis.workflow_run: PubAck(stream='github', seq=433413, domain=None, duplicate=None)


  - **application-server Logs**: Verify whether those same events appear in ``application-server`` logs. If the listener shows events but the ``application-server`` does not, that indicates a NATS delivery issue.


3. **Search for Consumer Deleted Errors**

  In the ``application-server`` logs, search for ``Consumer Deleted``, ``pullStatusError`` or ``heartbeatAlarm`` to confirm that the NATS consumer was removed. This is a clear sign that the server cannot receive new messages.

Remediation Steps
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you have confirmed that the ``application-server`` is not receiving events due to a deleted consumer, perform the following steps to restore processing:

1. **Stop Helios**

   .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml down

2. **Remove the NATS Data Volume**
    This will clear the NATS stream and allow the consumer to be recreated:

    .. code-block:: console

        docker volume rm helios_nats-data

3. **Restart Helios**

   .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml --env-file=.env up --pull=always -d


This sequence will clear any stale NATS state and allow a fresh consumer to be created.

For more details, refer to the issue and PR that introduced durable consumer support:

- Issue: https://github.com/ls1intum/Helios/issues/352
- PR: https://github.com/ls1intum/Helios/pull/349

Disaster Recovery
-------------------

In the event that the Helios database volume is accidentally removed, there is no direct backup available. To recover:

1. **Edit the .env File**

   Before starting Helios, open the ``.env`` file (located in ``/opt/helios``) and set:

   .. code-block:: ini

       DATA_SYNC_RUN_ON_STARTUP=true

   This ensures that Helios will run the full data synchronization process on startup.

2. **Start Helios for Data Synchronization**

   Because ``DATA_SYNC_RUN_ON_STARTUP`` is ``true``, Helios will repopulate repository metadata and configuration from GitHub. The sync can take up to **30–40** `minutes`, depending on the number and size of repositories.

   .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml --env-file=.env up --pull=always -d

3. **Reconfigure Repository Settings**

   After synchronization completes, each repository admin must log in to the Helios UI and reapply repository-specific settings:

   - Repository settings: Workflow UI grouping
   - Repository settings: Test artifact labeling
   - Environment settings: Setting up environment URLs, labels, etc.

   This manual step typically takes **5** `minutes` per repository.

4. **Disable Startup Sync**

   Once all repositories are reconfigured, return to the `.env` file and set:

   .. code-block:: ini

       DATA_SYNC_RUN_ON_STARTUP=false

   This prevents redundant full syncs on subsequent restarts.

Connecting to the Database via SSH Tunnel
--------------------------------------------

To connect to the PostgreSQL database running inside the Helios host from your local machine, you can establish an SSH tunnel. First, ensure that your SSH config defines a host alias (e.g., "helios") pointing to the server. Then run:

.. code-block:: console

    ssh -N -L 5433:localhost:5432 helios

- ``-N``: Do not execute a remote command—just forward ports.
- ``-L 5433:localhost:5432``: Forward local port ``5433`` to remote port ``5432`` on the Helios host.
- ``helios``: SSH host alias (or replace with ``username@hostname`` if no alias is defined).

Once the tunnel is established, connect locally to port ``5433`` as if you were connecting directly to the database:

.. code-block:: console

    psql -h localhost -p 5433 -U <db_user> -d <db_name>

Replace ``<db_user>`` and ``<db_name>`` with the appropriate PostgreSQL username and database name. You can view the actual credentials (``username``, ``password``, ``database name``) in the ``.env`` file under ``/opt/helios``.


Deployment User
-----------------

Helios deployments on GitHub Actions are performed by a dedicated user account named ``github_deployment``. This user was created following the instructions in the `ls1intum` GitHub organization’s repository (see ``https://github.com/ls1intum/.github/``). GitHub Actions uses ``github_deployment`` to push updated images and configuration into the Helios environment, so ensure that:

- The ``github_deployment`` user has the correct SSH keys and permissions configured on the Helios host.
- Any workflow secrets referencing deployment keys are up to date.
- The home directory for ``github_deployment`` (e.g., ``/home/github_deployment/.ssh/``) contains the authorized private key.
- The ``github_deployment`` user is a member of the ``docker`` group so it can run Docker commands during deployment.



Useful Environment Variables
-------------------------------
Helios relies on a set of environment variables defined in the ``.env`` file (located in ``/opt/helios``) to configure certain runtime behaviors and image tags. Common variables include:

.. code-block:: console

    # Enable or disable sending emails (true/false)
    EMAIL_ENABLED=true

    # Image tags for various Helios services
    CLIENT_IMAGE_TAG=latest
    APPLICATION_SERVER_IMAGE_TAG=latest
    NOTIFICATION_SERVER_IMAGE_TAG=latest
    WEBHOOK_LISTENER_IMAGE_TAG=latest
    KEYCLOAK_IMAGE_TAG=latest

    # Run data synchronization on startup (true/false)
    DATA_SYNC_RUN_ON_STARTUP=false

    # Time (in minutes) after which an inactive consumer is removed
    NATS_CONSUMER_INACTIVE_THRESHOLD_MINUTES=30
    # Time (in seconds) that NATS waits for a message ACK before resending
    NATS_CONSUMER_ACK_WAIT_SECONDS=60


- **EMAIL_ENABLED**: Controls whether the Helios application sends notification emails. Set to ``false`` to disable email functionality.
- **Image Tag Variables**:

  - ``latest`` refers to the most recent release.
  - For the latest ``staging`` build, set to ``staging``.
  - For specific staging builds, use the format: ``sha-<short-sha>-staging``

- **DATA_SYNC_RUN_ON_STARTUP**: If set to ``true``, Helios will run the data synchronization process each time it starts up. Set to ``false`` to disable automatic sync on boot.
- **NATS_CONSUMER_INACTIVE_THRESHOLD_MINUTES**: Defines how long (in minutes) a NATS consumer can be inactive before it is automatically removed. Default is 30 minutes.
- **NATS_CONSUMER_ACK_WAIT_SECONDS**: Specifies the time (in seconds) that NATS waits for a message acknowledgment before resending it. Default is 60 seconds.

Useful Server Commands
-------------------------

- **Running the Helios**

  To start the entire Helios application stack in detached mode and always pull the latest images:

  .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml --env-file=.env up --pull=always -d

- **Stopping Helios**

  To stop and remove all Helios containers, networks, and images (including orphaned containers):

  .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml down --remove-orphans --rmi all

- **Viewing All Container Logs**

  To follow logs for every container defined in the Compose file:

  .. code-block:: console

      cd /opt/helios
      docker compose -f compose.prod.yaml logs -f

- **Viewing an Individual Container’s Logs**

  List running containers to identify the container name:

  .. code-block:: console

      docker ps

  Then, view logs for a specific container (replace `container_name` with the actual name):

  .. code-block:: console

      docker logs -f container_name

- **Inspecting Disk and Volume Usage**

  To view Docker’s disk usage, including local volumes and their sizes:

  .. code-block:: console

      docker system df -v

  Example output:

  .. code-block:: console

      Local Volumes space usage:
      VOLUME NAME        LINKS     SIZE
      helios_nats-data   1         5.91GB
      helios_db-data     1         14.87GB

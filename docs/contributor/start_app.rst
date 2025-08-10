========================
Starting the Application
========================

Please be sure, that you set up the project correctly. If you haven't done this yet, please follow the `Setup Guide <../contributor/setup.html>`_.

Running Docker
--------------

For local development, in the root directory of the project we will run the docker compose up command which runs the file ``compose.yml``. This will start ``postgres``, ``keycloak``, ``webhook-listener``, ``nats-server``, ``client``, ``notification`` and ``mailhog``.

To run the docker containers, you can use the following command: ``docker compose up --build``.

Running Server
--------------
Please be sure, that the docker containers are running. The server instance needs a running postgres container. You can check this by running ``docker ps`` in your terminal.

To run the server, follow these steps in a new terminal:

1. **Go to the server directory**: Run the command ``cd server`` in the terminal.
2. **Run Server**: Run the command ``./gradlew :application-server:bootRunDev`` in the server directory of the project.
3. **Check Server Status**: You can check if the server is running by accessing `http://localhost:8080/status/health <http://localhost:8080/status/health>`_.

Running ngrok
--------------
To expose the webhook listener port to the internet, you have to start ngrok by running the command ``ngrok start webhook`` in a separate terminal.

If you haven't followed the setup guide and ``ngrok.yml`` is not configured yet, you can run ``ngrok http 4201`` to expose the webhook listener port. This will create a random public URL. Be careful, since this URL will change every time you start ngrok. Please configure ngrok following `Setting Up ngrok Locally <setup.html#setting-up-ngrok-locally>`_.

.. note::
  The server will start and you can access the application (client) at `http://localhost:4200 <http://localhost:4200>`_.

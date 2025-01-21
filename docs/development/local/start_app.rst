========================
Starting the Application
========================

Please be sure, that you set up the project correctly. If you haven't done this yet, please follow the `Setup Guide <setup.html>`_.

Running Docker
--------------

To run the docker containers, you can use the following command: ``docker compose up --build``. 

This command will start the postgres container, the server container, the nats-server, the webhook-listener as well as the client.

To expose the webhook listener port to the internet, you have to start ngrok by running the command ``ngrok http 4201`` in a separate terminal.

Running Server
--------------

Please be sure, that the docker containers are running. The server instance needs a running postgres container. You can check this by running ``docker ps`` in your terminal.

To run the server, follow these steps:

1. **Go to the application-server directory**: Run the command ``cd server/application-server`` in the terminal.
2. **Build Server**: Run the command ``./gradlew build`` in the server directory of the project.
3. **Run Server**: Run the command ``./gradlew bootRunDev`` in the server directory of the project.
4. **Check Server Status**: You can check if the server is running by accessing `http://localhost:8080/status/health <http://localhost:8080/status/health>`_.


The server will start and you can access the application (client) at `http://localhost:4200 <http://localhost:4200>`_. You can login in the development mode with ``test`` as username and password.

================
Generate OpenAPI
================

OpenAPI, formerly Swagger, is a specification for building APIs. It standardizes the way APIs are described, including endpoints, request/response formats, and authentication. This helps create clear and comprehensive API documentation and share models with the client.

If you change data affecting the endpoints, you have to regenerate the OpenAPI file to keep the client and server in sync. The commands assume you are in the root directory of the project.

Server
------

You can generate the standardized OpenAPI file with following command:

.. code-block:: shell

    cd server
    ./gradlew :application-server:generateOpenApiDocs

Client
------

You can generate the models and tanstack query options for the client from the OpenAPI file (from the server) with following command:

.. code-block:: shell

    cd client
    yarn generate:openapi
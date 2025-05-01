=====================
Token Exchange Setup
=====================

This document describes how to set up token exchange functionality in Keycloak 26.1.3, which allows a client to obtain identity provider tokens of users.

Prerequisites
-------------

* Keycloak server with admin access
* A confidential client (public clients are not allowed for token exchange)
* Admin permissions to configure client permissions and policies
* Token exchange feature enabled (currently in preview)

Feature Enablement
--------------------

At the moment of development, token exchange is only available as a feature preview. To enable the feature, start Keycloak with the following feature flags:

.. code-block:: bash

   --features=token-exchange,admin-fine-grained-authz

Setup Steps
-----------

1. Create Token Exchange Client
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Log in to the Keycloak Admin Console
#. Navigate to the target realm (Helios)
#. Go to Clients and create a new client (e.g., helios-token-exchange)
#. Set the client as confidential (public clients are not allowed for token exchange)
    .. raw:: html

       <a href="/_static/images/token_exchange/confidential-client.png" target="_blank">
         <img src="/_static/images/token_exchange/confidential-client.png" alt="Repository selection screen" style="height: 512px;" />
       </a>

#. Generate and save the client secret (this client ID and secret will be used to access users' GitHub tokens)
    .. raw:: html

       <a href="/_static/images/token_exchange/client-credentials.png" target="_blank">
         <img src="/_static/images/token_exchange/client-credentials.png" alt="Repository selection screen" style="height: 512px;" />
       </a>


2. Configure User Impersonation Permission
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Follow the steps listed under "Direct Naked Impersonation" in the Keycloak documentation. 
https://www.keycloak.org/securing-apps/token-exchange#_direct_naked_impersonation

The section below duplicates the steps in the documentation in case the link is broken or changes.

#. Go to the Users tab
#. In the Permissions tab, click on the "impersonate" link
#. In the permission setup page:
   * Click on "Client details" in the breadcrumbs
   * Define a policy for this permission
#. Go to the Policies tab and create a client policy:
   * Enter the client ID that will perform the token exchange
   * Save the policy
#. Return to the users' "impersonation" permission
#. Add the client policy you just defined

3. Configure Identity Provider Token Exchange
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

#. Go to Identity Providers
#. Select the external identity provider (e.g., GitHub)
#. Enable 'Store tokens' option in the Settings tab
#. Go to the Permissions tab
#. Enable Permissions
#. Click on the "impersonate" link
#. Select the policy created earlier for the helios-token-exchange client

4. Making Token Exchange Requests
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To make a token exchange request, use the following endpoint:

.. code-block:: bash

   curl -X POST \
       -d "client_id=<your-client-id>" \
       -d "client_secret=<your-client-secret>" \
       --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:token-exchange" \
       -d "requested_subject=<target-username>" \
       --data-urlencode "requested_token_type=urn:ietf:params:oauth:token-type:access_token" \
       -d "requested_issuer=<identity-provider>" \
       http://localhost:8081/realms/<your-realm>/protocol/openid-connect/token

Parameters:
* ``client_id``: The ID of your token exchange client
* ``client_secret``: The client's secret
* ``grant_type``: Must be "urn:ietf:params:oauth:grant-type:token-exchange"
* ``requested_subject``: The username or user ID to impersonate
* ``requested_token_type``: The type of token to request (e.g., access_token)
* ``requested_issuer``: The identity provider (e.g., github)

Example Response
-----------------

A successful response will include an access token:

.. code-block:: json

   {
      "access_token": "<provider-specific-token>",
      "expires_in": 0,
      "refresh_expires_in": 0,
      "not-before-policy": 0,
      "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
      "account-link-url": "<account-link-url>"
   }

NOTE:
--------------

GitHub access tokens are valid for 8 hours. Identity provider tokens are not refreshed automatically in Keycloak.
In order to make sure you always have a valid token, limit the session to 8 hours.

Security Considerations
-----------------------

1. Only use confidential clients for token exchange
2. Keep client credentials secure and never expose them
3. Limit the number of clients that have token exchange permissions
4. Regularly audit which clients have token exchange permissions
5. Monitor token exchange usage for suspicious patterns

Error Handling
--------------

Common error responses:

* 403 Forbidden: Client lacks required permissions
* 400 Bad Request: Invalid parameters or unsupported grant type
* 401 Unauthorized: Invalid client credentials

Troubleshooting
---------------

If you encounter issues:

1. Verify client permissions are properly configured
2. Ensure the client is confidential (not public)
3. Check that the target user exists and is active
4. Verify all required parameters are provided
5. Check Keycloak server logs for detailed error messages

For additional information, refer to the `Keycloak documentation <https://www.keycloak.org/securing-apps/token-exchange>`_.

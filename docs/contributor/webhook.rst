===================
Webhook from GitHub
===================

The webhook listener service listens for events from GitHub repositories and publishes them to a NATS server. 
This service is essential for real-time event processing and integration with GitHub (e.g. branch creation).

Events will be published to NATS with the subject:

``github.<owner>.<repo>.<event_type>``


Setting Up ngrok Locally
---------------

To expose a local webhook listener to GitHub, you can use ngrok. Follow these steps:

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

3. **Configure ngrok**
   By default, ngrok looks for a config file at::

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
   - ``name``: A label for this tunnel (e.g., ``webhook``).
   - ``url``: Your persistent domain (explained below).
   - ``upstream.url``: The local port where your webhook listener is running (e.g., ``4201``).

4. **Verify Your Configuration**
   Run the following command::

       ngrok config check

   If everything is set up correctly, you should see something like::

       Valid configuration file at /Users/you/Library/Application Support/ngrok/ngrok.yml

5. **Reserve a Persistent Domain**
   By default, free ngrok tunnels use a random subdomain each time you start them. To avoid updating your GitHub webhook URL on every restart, reserve one persistent domain:

   - In the ngrok dashboard, navigate to **Universal Gateway → Domains**.
   - Click **New Domain** and follow the prompts to acquire a free persistent domain (e.g., ``<YOUR_PERSISTENT_DOMAIN>.ngrok-free.app``).
   - Update ``ngrok.yml`` under ``endpoints → url`` with your new domain.

6. **Run ngrok**
   With your configuration in place, start ngrok::

       ngrok start webhook

   This reads the ``webhook`` entry in ``ngrok.yml``, creates a tunnel, and prints the public URL. Point your GitHub webhook to::

       https://<YOUR_PERSISTENT_DOMAIN>.ngrok-free.app

Now, whenever GitHub sends an event to that URL, ngrok forwards it to your local service on port 4201. The listener picks it up and publishes it to NATS under the subject ``github.<owner>.<repo>.<event_type>``.
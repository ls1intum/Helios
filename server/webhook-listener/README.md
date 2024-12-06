
# WebHook Listener

## Overview

A service to listen GitHub webhooks and publish the data to NATS JetStream.

## Prerequisites

- Python 3.12 (or higher)

## Getting Started

### 1. Clone the Repository

Clone the Helios repository to your local machine.

```bash
git clone https://github.com/ls1intum/Helios.git
cd helios/server/webhook-listener
```

### 2. Create a virtual environment and install dependencies

```bash
$ python3 -m venv .venv
$ source .venv/bin/activate
$ pip install -r requirements.txt
```

### 3. Setup configuration and environment

Copy the file `.env.example` to `.env` and adjust the values to your needs. It is set up to work with the Docker Compose setup (see [here](../../README.md#development-setup)).

```bash
$ cp .env.example .env
```

Environment variables to set:
- `NATS_URL`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `WEBHOOK_SECRET`: HMAC secret for verifying GitHub webhooks

You'll need to create a GitHub webhook in your repository and set the secret to the value of `WEBHOOK_SECRET`. The webhook should be set to send payloads to: `https://<server>:4200/github`.

### 4. Set up Ngrok (optional)

In case you are developing locally, you can use [ngrok](https://ngrok.com/) to expose your local server to the internet:

```bash
$ ngrok http 4200
```

### 5. Run the listener

```bash
# For development (with auto-reload)
$ fastapi dev
# For production
$ fastapi run
```

Events will be published to NATS with the subject:

```
github.<owner>.<repo>.<event_type>
```

## Important Notes

- The service automatically sets up a NATS JetStream stream named `github` to store events.
- Ensure your firewall allows traffic on port 4222 (NATS).
- Authentication tokens are crucial for securing the NATS server and ensuring only authorized clients can connect.
- The webhook listener service connects to the NATS server like any other client using the specified URL and token.
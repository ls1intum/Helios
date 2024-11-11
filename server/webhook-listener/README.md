# WebHook Listener

## Overview

A service to listen GitHub webhooks and publish the data to NATS JetStream.

## Setup

### Prerequisites

- **Python 3.x.x**
- **Docker** for containerization

## Environment Variables

- `NATS_URL`: NATS server URL
- `NATS_AUTH_TOKEN`: Authorization token for NATS server
- `WEBHOOK_SECRET`: HMAC secret for verifying GitHub webhooks

If you are using docker compose, you don't need to set NATS_URL for local development.

Generate an AUTH TOKEN and Set the environment variable:
  
```bash
openssl rand -hex 48 # Generate a random token, save this token to use it in application-server
export NATS_AUTH_TOKEN=<generated-token>
```

Add Webhook in GitHub repository and set the secret:
```bash
export WEBHOOK_SECRET=<webhook-secret>
```

## Running with Docker Compose

Build and run with Docker Compose:

```bash
docker-compose up --build
```

Service ports:
- **Webhook Service**: `4200`
- **NATS Server**: `4222`


## Usage

Configure your GitHub webhooks to POST to:

```
https://<server>:4200/github
```

### Event Handling

Events are published to NATS with the subject:

```
github.<owner>.<repo>.<event_type>
```



## Setup for Local Development
### Installation

Install dependencies:

```bash
pip install -r requirements.txt
```

### Running Service


```bash
fastapi dev #For Development
fastapi run #For Production
```

### Important Notes

- The service automatically sets up a NATS JetStream stream named `github` to store events.
- Ensure your firewall allows traffic on port 4222 (NATS).
- Authentication tokens are crucial for securing the NATS server and ensuring only authorized clients can connect.
- The webhook listener service connects to the NATS server like any other client using the specified URL and token.
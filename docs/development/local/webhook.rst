===================
Webhook from GitHub
===================

The webhook listener service listens for events from GitHub repositories and publishes them to a NATS server. 
This service is essential for real-time event processing and integration with GitHub (e.g. branch creation).

Events will be published to NATS with the subject:

```
github.<owner>.<repo>.<event_type>
```


Important Notes
---------------

- The service automatically sets up a NATS JetStream stream named `github` to store events.
- Ensure your firewall allows traffic on port 4222 (NATS).
- Authentication tokens are crucial for securing the NATS server and ensuring only authorized clients can connect.
- The webhook listener service connects to the NATS server like any other client using the specified URL and token.
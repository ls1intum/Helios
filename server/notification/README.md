# Email Notification Service

This document explains how to use the email notification functionality in the Helios notification server.

## Configuration

Email settings are configured through the following environment variables:

- `MAIL_HOST`: SMTP server hostname (default: localhost)
- `MAIL_PORT`: SMTP server port (default: 1025)
- `EMAIL_ENABLED`: Enable/disable email sending (default: true)
- `EMAIL_FROM`: Default sender email address (default: helios-local@aet.cit.tum.de)

In the production environment, the notification server is configured to use the VM's Postfix server at 192.168.1.1:25.

Production email is helios@aet.cit.tum.de and staging email is helios-staging@aet.cit.tum.de

In the development environment, the notification server is configured to use a local SMTP server (e.g., MailHog) for
testing purposes.

## NATS Message Format

To send an email via NATS, publish a message to the `notification.message.email` subject with the following JSON
payload:

```json
{
  "to": "recipient@example.com",
  "subject": "Email Subject",
  "body": "<h1>Hello</h1><p>This is the email body in HTML format</p>"
}
```

Required fields:

- `to`: Recipient email address
- `subject`: Email subject
- `body`: Email body content (HTML format is supported)

Example using the application server:

```java
// Create email data
Map<String, Object> emailData = new HashMap<>();
emailData.

put("to","user@example.com");
emailData.

put("subject","Notification from Helios");
emailData.

put("body","<h1>Hello</h1><p>This is a notification from Helios.</p>");

// Convert to JSON bytes
byte[] jsonData = objectMapper.writeValueAsBytes(emailData);

// Publish via NATS
natsNotificationPublisherService.

publishNotification("notification.message.email",jsonData);
```

## Testing Endpoints

The notification server provides test endpoints for sending emails and viewing the email history in development mode:

### Send Test Email

```
POST /api/test/email/send?to=recipient@example.com&subject=Test&body=<h1>Test</h1>
```

Parameters:

- `to`: Recipient email address (required)
- `subject`: Email subject (required)
- `body`: Email body in HTML format (optional, defaults to a basic test email)

## Development vs Production

- **Development**: Emails are sent to the MailHog server (which runs as a container) for testing purposes. You can view
  the emails in the MailHog web interface at `http://localhost:8025`. The email content is not actually sent to the
  recipient.
- **Production**: Emails are actually sent via the configured SMTP server (Postfix in the VM).

## Running Locally

There are two ways to run the email notification locally:

1. **Run Notification Service in Compose**: Local development compose consists of a notification microservice and a
   MailHog container. The MailHog container is used to capture emails sent by the notification service for testing
   purposes.

Compose can be started with the following command:

```bash
  docker compose up --build
```

2. **Run Notification Service individually**: If you are developing the notification service, you can run the
   notification service separately.

Make sure to point out the MailHog server that is running in the compose file. The ports of the MailHog server are
mapped to the host machine, so you can access it via `localhost:8025` and `localhost:1025`.

Make sure your `.env` file contains the following settings:

```env
MAIL_HOST=localhost
MAIL_PORT=1025
EMAIL_ENABLED=true
```

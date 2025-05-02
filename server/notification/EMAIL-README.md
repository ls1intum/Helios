# Email Notification Service

This document explains how to use the email notification functionality in the Helios notification server.

## Configuration

Email settings are configured through the following environment variables:

- `MAIL_HOST`: SMTP server hostname (default: 192.168.1.1)
- `MAIL_PORT`: SMTP server port (default: 25)
- `EMAIL_ENABLED`: Enable/disable email sending (default: true)
- `EMAIL_DEV_MODE`: Enable development mode which logs emails instead of sending them (default: false)
- `EMAIL_FROM`: Default sender email address (default: helios@aet.cit.tum.de)

In the production environment, the notification server is configured to use the VM's Postfix server at 192.168.1.1:25.

In the development environment, the notification server is configured with dev-mode enabled, which means emails are logged rather than actually sent.

## NATS Message Format

To send an email via NATS, publish a message to the `notification.message.email` subject with the following JSON payload:

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
emailData.put("to", "user@example.com");
emailData.put("subject", "Notification from Helios");
emailData.put("body", "<h1>Hello</h1><p>This is a notification from Helios.</p>");

// Convert to JSON bytes
byte[] jsonData = objectMapper.writeValueAsBytes(emailData);

// Publish via NATS
natsNotificationPublisherService.publishNotification("notification.message.email", jsonData);
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

### View Email History (Dev Mode Only)

```
GET /api/test/email/history
```

This endpoint returns a list of emails that would have been sent in development mode.

## Development vs Production

- **Development**: Emails are logged to the console and stored in memory, accessible via the `/api/test/email/history` endpoint.
- **Production**: Emails are actually sent via the configured SMTP server (Postfix in the VM). 
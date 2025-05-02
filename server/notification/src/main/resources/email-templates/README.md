# Email Templates

This directory contains HTML email templates used by the notification service. Templates use a simple `${placeholder}` syntax for dynamic content.

## Available Templates

### welcome.html
A welcome email template for new users.

**Required parameters:**
- `username`: The user's name
- `loginUrl`: URL for the login page

**Example usage:**
```json
{
  "to": "user@example.com",
  "template": "welcome",
  "subject": "Welcome to Helios",
  "parameters": {
    "username": "John Doe",
    "loginUrl": "https://helios.tum.de/login"
  }
}
```


## Creating New Templates

1. Create a new HTML file in this directory (e.g., `alert.html`)
2. Use `${parameterName}` syntax for dynamic content
3. Include inline CSS for styling (email clients have limited CSS support)
4. Test your template using the `/api/test/email-templates/{templateName}` endpoint

## Default Parameters

These parameters are automatically available in all templates:
- `currentYear`: The current year (e.g., 2023)

## Testing Templates

Use the test endpoints to try out templates:

- `GET /api/test/email-templates`: List all available templates
- `POST /api/test/email-templates/{templateName}`: Send a test email with any template

## NATS Message Format

To send an email via NATS using a template, publish a message with this format:

```json
{
  "to": "recipient@example.com",
  "template": "templateName",
  "subject": "Email Subject",
  "parameters": {
    "param1": "value1",
    "param2": "value2"
  }
}
```

Send to subject: `notification.message.email` 
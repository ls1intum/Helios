# Helios :sun_with_face:
The personification of the sun, symbolizing light, clarity, and visibility that this app brings to CI/CD processes and test reliability

### Empowering Developers with a Dynamic User Interface for GitHub Actions

Welcome to the GitHub Actions UI Enhancement project! This project aims to develop a web application that enhances the usability of GitHub Actions by providing a dynamic user interface for developers. The application addresses key limitations such as the absence of a graphical interface, challenges in visualizing deployment targets, test case failures, and flaky tests.


### Greek mythology

Helios was the Greek god and personification of the sun :sun_with_face: Each day, he was said to drive a golden chariot across the sky, bringing light to the world. According to mythology, Helios rose in the east and traveled across the heavens, guiding his chariot pulled by four horses. His daily journey from dawn to dusk symbolized reliability, consistency, and illumination.

Helios was also often depicted with a radiant crown, symbolizing the sun's light, and was considered an all-seeing deity, which aligns well with themes of visibility and clarity. These qualities made Helios a powerful symbol of insight and oversight in Greek culture.

## Development Setup

Helios consists of multiple components and dependencies. Setting up the dependencies (e.g. PostgreSQL, NATS Server, Angular Client) is easy using the Docker `docker-compose.yaml` file.

```bash
# Set the webhook secret so the listener works (alternatively put the following within a .env file)
$ export WEBHOOK_SECRET=<your_webhook_secret>

$ docker compose up

# In case you want to run the webhook listener without Docker (you'll have to set the WEBHOOK_SECRET in the respective .env file)
$ docker compose --scale webhook-listener=0
```

Components such as the application server *have to* be started separately. Take a look at the `README.md` files in the respective directories for more information. 

For more information on setting up the Webhook and running the listener locally with Ngrok, see [here](server/webhook-listener/README.md#4-set-up-ngrok-optional).
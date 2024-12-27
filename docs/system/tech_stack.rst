=================
Tech Stack
=================

Technologies Used
-----------------

- **Programming Languages**:
  - *Java*: Utilized for server-side development, handling the core application logic.
  - *TypeScript*: Employed in the client-side development for building interactive user interfaces.
  - *Python*: Used for specific scripting tasks within the project.
  - *JavaScript*: Incorporated for additional client-side functionalities.
  - *HTML*: Markup language for structuring web content.
  - *Dockerfile*: Scripts to create Docker images for containerization.

- **Frameworks and Libraries**:
  - *Angular*: A platform for building mobile and desktop web applications, used in the client component.
  - *JUnit 5*: A testing framework for Java applications, ensuring code reliability.
  - *OpenAPI*: Utilized for API documentation and client generation.

- **Databases**:
  - *PostgreSQL*: A powerful, open-source object-relational database system used for data storage.

- **Messaging Systems**:
  - *NATS Server*: A lightweight, high-performance messaging system employed for communication between services.

- **Containerization and Orchestration**:
  - *Docker*: Used to containerize the application components, ensuring consistency across different environments.
  - *Docker Compose*: Facilitates the setup of the development environment by managing multi-container Docker applications.

- **Web Servers**:
  - *NGINX*: Serves as a reverse proxy and load balancer for handling client requests.

Development Setup
-----------------

To set up the development environment for Helios, follow these steps:

1. **Set the Webhook Secret**: Configure the webhook secret to ensure secure communication.
   ```bash
   export WEBHOOK_SECRET=<your_webhook_secret>

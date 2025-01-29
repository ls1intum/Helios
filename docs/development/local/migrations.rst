===================
Database migrations
===================

We are using `Flyway <https://flywaydb.org/>`__ for database migrations. When changing the database schema, create a new migration file in ``src/main/resources/db/migration`` within the application server with the following naming convention: ``V<VERSION-NUMBER>__<description>.sql``, e.g. ``V1__create_users.sql``.

The migrations are automatically applied when starting the application.

Branching Strategy
------------------
1. Create migration files in feature branches
2. Prefix with next available version (just increment the last version number)
3. Rebase if conflicts occur
4. Run migrations locally
5. Merge to main

**Important**: Never modify committed migrations!
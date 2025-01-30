===================
Database migrations
===================

We are using `Flyway <https://flywaydb.org/>`__ for database migrations. A GitHub Actions workflow validates these migrations during CI/CD by ensuring that the migration scripts are correctly formatted and can be applied without errors. for database migrations. When changing the database schema, create a new migration file in ``src/main/resources/db/migration`` within the application server with the following naming convention: ``V<VERSION-NUMBER>__<description>.sql``, e.g. ``V1__create_users.sql``.

The migrations are automatically applied when starting the application.

Branching Strategy
------------------
1. Create migration files in feature branches
2. Prefix with next available version (just increment the last version number)
3. Rebase if conflicts occur
4. Run migrations locally to verify that they work as expected, even though migrations are automatically applied on application startup
5. Merge to main

**Important**: Never modify committed migrations! If a migration needs to be corrected or updated, create a new migration file to apply the necessary changes rather than altering an existing one.
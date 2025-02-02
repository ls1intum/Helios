===================
Database Migrations
===================

Overview
--------
We use `Flyway <https://flywaydb.org/>`_ for database schema versioning and migrations. All database changes are tracked through version-controlled SQL scripts, ensuring consistent database states across all environments.

Migration Files
---------------
**Location**

All migration scripts should be placed in::

    src/main/resources/db/migration

**Naming Convention**

Format::

    V<VERSION-NUMBER>__<description>.sql

Example::

    V1__create_users.sql

The version number should be sequential and unique across all migrations.

Branching Strategy
------------------
1. Create migration files in feature branches
2. Use the next available version number (increment from the last existing version)
3. Rebase if conflicts occur
4. Test migrations locally before merging (just start application-server to apply migrations)
5. Merge to main

.. warning::

   Never modify existing/committed migrations! Create new migration files for any necessary changes.

Handling Migration Conflicts
----------------------------
When working with multiple branches, you may encounter migration conflicts. Here's how to handle them:

1. Clean existing migrations::

    ./gradlew flywayClean flywayMigrate

2. Apply current branch migrations:

   * Start application-server to apply migrations automatically

3. When switching branches, clean and reapply migrations again::

    ./gradlew flywayClean flywayMigrate



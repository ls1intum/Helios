# PostgreSQL major version upgrade (logical dump and restore)

A PostgreSQL data directory cannot be opened by a different major version, and the official
images from version 18 on keep the versioned data directory below `/var/lib/postgresql` instead of
`/var/lib/postgresql/data`. The upgrade therefore moves the data into a **new Docker volume** with a
logical dump and restore. The old volume is never modified, which makes rollback a configuration
change.

Used for 17 to 18 (staging, then production). For the next major version repeat the procedure with a
volume named `db-data-pg<major>`.

> **Do not merge the compose change before the data is in the new volume.** The compose file points
> the `postgres` service at the new, empty volume; merging it to `staging` makes CI redeploy
> staging immediately, and the application would start on an empty database (Flyway would create an
> empty schema). Restore the data first (steps 3 to 5), then merge or copy the compose file.

All commands run on the Docker host in the compose project directory (`/opt/helios`). The services
use the project name `helios`, so the volumes are `helios_db-data` (old) and `helios_db-data-pg18`
(new). Variables come from `.env` (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`).

## 0. Before you start

- A recent, verified backup exists (`helios-backup.sh`, nightly at 02:00). Take another one right
  before the cut-over on production.
- Free disk space for the new volume and the dumps: about three times the size of the databases.
- Optional: remove data that is not needed any more first, it makes the dump and restore faster.
  For example orphaned large objects: `vacuumlo -n -U $POSTGRES_USER helios` (dry run), then
  `vacuumlo` and `VACUUM FULL pg_largeobject`.
- Extensions that the target image does not ship must be dropped in the source first
  (`DROP EXTENSION pg_repack;`). `pgstattuple` is part of the image and is restored as is.

## 1. Record the state of the old database

```sh
docker compose -f compose.prod.yaml --env-file .env exec -T postgres \
  psql -U "$POSTGRES_USER" -d helios -tA -F'|' -c "
  select relname, n_live_tup from pg_stat_user_tables order by relname" > /tmp/rows-before.txt
docker compose -f compose.prod.yaml --env-file .env exec -T postgres \
  psql -U "$POSTGRES_USER" -d helios -tAc "select pg_size_pretty(pg_database_size('helios'))"
```

`n_live_tup` is an estimate; for an exact comparison use `select count(*)` on the tables that
matter (`workflow_run`, `deployment`, `pull_request`, `user`).

## 2. Stop the writers

Keep `postgres` and `nats` running. GitHub events that arrive meanwhile are buffered by NATS and
processed after the restart.

```sh
docker compose -f compose.prod.yaml --env-file .env stop \
  application-server notification keycloak webhook-listener client
```

## 3. Dump both databases

```sh
mkdir -p /opt/backups/pg-upgrade && cd /opt/backups/pg-upgrade
for db in helios keycloak; do
  docker compose -f /opt/helios/compose.prod.yaml --env-file /opt/helios/.env exec -T postgres \
    pg_dump -U "$POSTGRES_USER" -Fc --no-owner --no-privileges -d "$db" > "$db.dump"
done
ls -lh *.dump
```

Both files must be non-empty. `pg_restore --list helios.dump | head` must work.

## 4. Restore into the new volume with a throw-away container

The container uses the same image tag as the compose file and the same credentials, so the role
and its password match what the applications use.

```sh
docker run -d --name pg-restore \
  -e POSTGRES_USER="$POSTGRES_USER" -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" -e POSTGRES_DB=helios \
  -v helios_db-data-pg18:/var/lib/postgresql postgres:18.7-alpine
until docker exec pg-restore pg_isready -U "$POSTGRES_USER" -d helios; do sleep 2; done
docker exec pg-restore psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE keycloak;"
for db in helios keycloak; do
  docker exec -i pg-restore pg_restore -U "$POSTGRES_USER" -d "$db" --no-owner --exit-on-error \
    < "/opt/backups/pg-upgrade/$db.dump"
done
```

`pg_restore` must finish without errors. (The initial `helios` database created by the image is
empty, the keycloak database is created here; the compose file's start command creates it too, and
leaves an existing one alone.)

## 5. Verify, then stop the container

```sh
docker exec pg-restore psql -U "$POSTGRES_USER" -d helios -tA -F'|' -c "
  select relname, n_live_tup from pg_stat_user_tables order by relname" > /tmp/rows-after.txt
diff /tmp/rows-before.txt /tmp/rows-after.txt && echo "row estimates identical"
docker exec pg-restore psql -U "$POSTGRES_USER" -d helios -tAc \
  "select version, success from flyway_schema_history order by installed_rank desc limit 1"
docker exec pg-restore psql -U "$POSTGRES_USER" -d helios -c "ANALYZE;"
docker stop pg-restore && docker rm pg-restore
```

Compare exact counts of the important tables with the old database as well, and make sure the last
Flyway migration is the same as before. Estimates can differ slightly after `ANALYZE`; large
differences are not acceptable.

## 6. Switch over

Put the new compose file in place (merge to `staging` for the staging environment, copy for
production) and start everything:

```sh
docker compose -f compose.prod.yaml --env-file .env up -d --remove-orphans
```

Check `docker compose ps`, the application log (`Successfully validated NN migrations`, `Schema
"public" is up to date`), Keycloak, and run the smoke test.

## 7. Roll back

Until the old volume is deleted, rollback is: restore the previous compose file (image
`postgres:17.12-alpine`, mount `db-data:/var/lib/postgresql/data`) and run `docker compose up -d`.
Changes made after the switch-over are not in the old volume. If they matter, dump the new database
and restore it into the old one before switching back.

## 8. Clean up

After the soak period (a few days on staging, at least a week on production): remove the old volume
(`docker volume rm helios_db-data`) and the dumps in `/opt/backups/pg-upgrade`.

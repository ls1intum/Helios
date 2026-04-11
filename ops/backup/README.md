# Helios VM Backup

These scripts are meant to live in git, while the actual backup data stays outside the repository.

The deployment workflows do not copy the repository to the VM. They copy `compose.prod.yaml` into the VM working directory and then create `.env` there. That means these scripts should be versioned here, but installed separately on the VM, for example under `/usr/local/bin`.

## What gets backed up

- `/opt/helios/.env`
- `/opt/helios/heliosapp.converted_key_pkcs8.pem`
- `/opt/helios/helios-realm.json`
- `/opt/helios/compose.prod.yml` or `/opt/helios/compose.prod.yaml`
- `/etc/nginx/conf/nginx.conf`
- PostgreSQL globals dump for roles and related cluster metadata
- PostgreSQL SQL dumps for the application database and the `keycloak` database
- Optional `helios_nats-data` archive

The nightly backup intentionally does not copy the live PostgreSQL volume. It uses `pg_dump`, which is the safer online backup method.

The backup also includes `globals.sql.gz`, produced by `pg_dumpall --globals-only`, so roles such as `helios` can be recreated when you need a more faithful restore target.

## Install on VM

Copy the scripts from the repo to the VM once and run them from there:

```bash
install -m 0755 ops/backup/helios-backup.sh /usr/local/bin/helios-backup.sh
install -m 0755 ops/backup/helios-restore.sh /usr/local/bin/helios-restore.sh
```

## Backup usage

Run manually:

```bash
/usr/local/bin/helios-backup.sh
```

Useful overrides:

```bash
DEPLOY_DIR=/opt/helios \
BACKUP_BASE=/opt/backups/helios \
RETENTION_DAYS=14 \
INCLUDE_NATS_VOLUME=true \
/usr/local/bin/helios-backup.sh
```

Each run creates a timestamped folder under `BACKUP_BASE`.

If `pv` is installed on the VM, the script shows approximate interactive progress for each database dump. This is based on `pg_database_size`, so treat the percentage as an estimate, not an exact measure of the final gzip size.

You can control that behavior with `SHOW_PROGRESS=auto|true|false`:

```bash
SHOW_PROGRESS=true /usr/local/bin/helios-backup.sh
```

## Restore usage

Restore is destructive and requires `--force`:

```bash
/usr/local/bin/helios-restore.sh \
  --backup-dir /opt/backups/helios/2026-04-10_021500 \
  --force
```

If you are restoring manually into a fresh PostgreSQL instance, restore `globals.sql.gz` first, then the per-database dumps:

```bash
gunzip -c /opt/backups/helios/2026-04-10_021500/globals.sql.gz | psql -U postgres -d postgres
gunzip -c /opt/backups/helios/2026-04-10_021500/app.sql.gz | psql -U postgres -d helios_app
gunzip -c /opt/backups/helios/2026-04-10_021500/keycloak.sql.gz | psql -U postgres -d keycloak
```

Optional override to restore the NATS volume too:

```bash
RESTORE_NATS_VOLUME=true \
/usr/local/bin/helios-restore.sh \
  --backup-dir /opt/backups/helios/2026-04-10_021500 \
  --force
```

## Cron

Example cron entry:

```cron
15 2 * * * /usr/local/bin/helios-backup.sh >> /var/log/helios-backup.log 2>&1
```

If the deployment paths differ from the defaults, set them inline in cron:

```cron
15 2 * * * DEPLOY_DIR=/opt/helios BACKUP_BASE=/opt/backups/helios /usr/local/bin/helios-backup.sh >> /var/log/helios-backup.log 2>&1
```

## Notes

- The scripts expect Docker Compose v2 (`docker compose`) or `docker-compose`.
- By default the Docker Compose project name is derived from the deployment directory name. Override `COMPOSE_PROJECT_NAME` if your VM uses a different project name.

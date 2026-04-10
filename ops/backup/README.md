# Helios VM Backup

These scripts are meant to live in git, while the actual backup data stays outside the repository.

## What gets backed up

- `/opt/helios/.env`
- `/opt/helios/heliosapp.converted_key_pkcs8.pem`
- `/opt/helios/helios-realm.json`
- `/opt/helios/compose.prod.yml` or `/opt/helios/compose.prod.yaml`
- `/etc/nginx/conf/nginx.conf`
- PostgreSQL SQL dumps for the application database and the `keycloak` database
- Optional `helios_nats-data` archive

The nightly backup intentionally does not copy the live PostgreSQL volume. It uses `pg_dump`, which is the safer online backup method.

## Backup usage

Run manually:

```bash
./ops/backup/helios-backup.sh
```

Useful overrides:

```bash
DEPLOY_DIR=/opt/helios \
BACKUP_BASE=/opt/backups/helios \
RETENTION_DAYS=14 \
INCLUDE_NATS_VOLUME=true \
./ops/backup/helios-backup.sh
```

Each run creates a timestamped folder under `BACKUP_BASE`.

## Cron

Example cron entry:

```cron
15 2 */2 * * /path/to/repo/ops/backup/helios-backup.sh >> /var/log/helios-backup.log 2>&1
```

If the deployment paths differ from the defaults, set them inline in cron:

```cron
15 2 */2 * * DEPLOY_DIR=/opt/helios BACKUP_BASE=/opt/backups/helios /path/to/repo/ops/backup/helios-backup.sh >> /var/log/helios-backup.log 2>&1
```

## Notes

- The scripts expect Docker Compose v2 (`docker compose`) or `docker-compose`.
- By default the Docker Compose project name is derived from the deployment directory name. Override `COMPOSE_PROJECT_NAME` if your VM uses a different project name.

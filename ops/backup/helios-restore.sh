#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  helios-restore.sh --backup-dir /path/to/backup --force

Environment overrides:
  DEPLOY_DIR=/opt/helios
  ENV_FILE=/opt/helios/.env
  NGINX_CONFIG_PATH=/etc/nginx/conf/nginx.conf
  COMPOSE_PROJECT_NAME=helios
  RESTORE_NATS_VOLUME=false
EOF
}

DEPLOY_DIR="${DEPLOY_DIR:-/opt/helios}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
NGINX_CONFIG_PATH="${NGINX_CONFIG_PATH:-/etc/nginx/conf/nginx.conf}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$DEPLOY_DIR")}"
RESTORE_NATS_VOLUME="${RESTORE_NATS_VOLUME:-false}"

BACKUP_DIR=""
FORCE="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir)
      BACKUP_DIR="${2:-}"
      shift 2
      ;;
    --force)
      FORCE="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

resolve_compose_file() {
  if [[ -f "$DEPLOY_DIR/compose.prod.yml" ]]; then
    printf '%s\n' "$DEPLOY_DIR/compose.prod.yml"
    return
  fi

  if [[ -f "$DEPLOY_DIR/compose.prod.yaml" ]]; then
    printf '%s\n' "$DEPLOY_DIR/compose.prod.yaml"
    return
  fi

  die "Could not find compose.prod.yml or compose.prod.yaml in $DEPLOY_DIR"
}

set_compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE_CMD=(docker compose)
    return
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE_CMD=(docker-compose)
    return
  fi

  die "Neither 'docker compose' nor 'docker-compose' is available"
}

wait_for_postgres() {
  log "Waiting for PostgreSQL to become ready"
  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
    sh -lc 'until pg_isready -U "$POSTGRES_USER" >/dev/null 2>&1; do sleep 1; done'
}

reset_database() {
  local database_name="$1"

  log "Recreating database '$database_name'"
  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"$database_name\" WITH (FORCE);"
  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE \"$database_name\";"
}

restore_database() {
  local archive_name="$1"
  local database_name="$2"

  [[ -f "$BACKUP_DIR/$archive_name" ]] || die "Missing archive: $BACKUP_DIR/$archive_name"
  reset_database "$database_name"

  log "Restoring database '$database_name' from $archive_name"
  gunzip -c "$BACKUP_DIR/$archive_name" | \
    "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
      psql -U "$POSTGRES_USER" -d "$database_name" -v ON_ERROR_STOP=1
}

restore_file_if_present() {
  local source_path="$1"
  local target_path="$2"

  if [[ ! -f "$source_path" ]]; then
    log "Skipping missing file: $source_path"
    return
  fi

  mkdir -p "$(dirname "$target_path")"
  cp "$source_path" "$target_path"
}

restore_nats_volume() {
  local archive_path="$BACKUP_DIR/nats-volume.tar.gz"
  local volume_name="${COMPOSE_PROJECT_NAME}_nats-data"

  [[ -f "$archive_path" ]] || {
    log "Skipping NATS restore because archive is missing"
    return
  }

  if ! docker volume inspect "$volume_name" >/dev/null 2>&1; then
    log "Skipping missing Docker volume: $volume_name"
    return
  fi

  log "Restoring NATS volume '$volume_name'"
  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop nats-server
  docker run --rm -v "$volume_name":/target alpine \
    sh -c 'rm -rf /target/* /target/.[!.]* /target/..?*'
  docker run --rm \
    -v "$volume_name":/target \
    -v "$BACKUP_DIR":/backup:ro \
    alpine \
    sh -c 'cd /target && tar xzf /backup/nats-volume.tar.gz'
}

[[ -n "$BACKUP_DIR" ]] || die "--backup-dir is required"
[[ -d "$BACKUP_DIR" ]] || die "Backup directory does not exist: $BACKUP_DIR"
[[ "$FORCE" == "true" ]] || die "Restore is destructive. Re-run with --force after validating the backup directory."

set_compose_cmd

restore_file_if_present "$BACKUP_DIR/.env" "$ENV_FILE"
restore_file_if_present "$BACKUP_DIR/heliosapp.converted_key_pkcs8.pem" "$DEPLOY_DIR/heliosapp.converted_key_pkcs8.pem"
restore_file_if_present "$BACKUP_DIR/helios-realm.json" "$DEPLOY_DIR/helios-realm.json"
restore_file_if_present "$BACKUP_DIR/nginx.conf" "$NGINX_CONFIG_PATH"

if [[ -f "$BACKUP_DIR/compose.prod.yml" ]]; then
  restore_file_if_present "$BACKUP_DIR/compose.prod.yml" "$DEPLOY_DIR/compose.prod.yml"
  COMPOSE_FILE="$DEPLOY_DIR/compose.prod.yml"
elif [[ -f "$BACKUP_DIR/compose.prod.yaml" ]]; then
  restore_file_if_present "$BACKUP_DIR/compose.prod.yaml" "$DEPLOY_DIR/compose.prod.yaml"
  COMPOSE_FILE="$DEPLOY_DIR/compose.prod.yaml"
else
  COMPOSE_FILE="$(resolve_compose_file)"
fi

[[ -f "$ENV_FILE" ]] || die "Environment file does not exist after restore copy: $ENV_FILE"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

[[ -n "${POSTGRES_USER:-}" ]] || die "POSTGRES_USER is not set in $ENV_FILE"
[[ -n "${POSTGRES_DB:-}" ]] || die "POSTGRES_DB is not set in $ENV_FILE"

log "Stopping Helios services before database restore"
"${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop \
  application-server keycloak notification webhook-listener client nats-server || true
"${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d postgres

wait_for_postgres
restore_database "app.sql.gz" "$POSTGRES_DB"
restore_database "keycloak.sql.gz" "keycloak"

if [[ "$RESTORE_NATS_VOLUME" == "true" ]]; then
  restore_nats_volume
fi

log "Starting Helios stack"
"${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

log "Restore completed"

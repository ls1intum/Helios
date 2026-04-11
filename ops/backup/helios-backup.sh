#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/helios}"
BACKUP_BASE="${BACKUP_BASE:-/opt/backups/helios}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
NGINX_CONFIG_PATH="${NGINX_CONFIG_PATH:-/etc/nginx/conf/nginx.conf}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
INCLUDE_NATS_VOLUME="${INCLUDE_NATS_VOLUME:-true}"
SHOW_PROGRESS="${SHOW_PROGRESS:-auto}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$DEPLOY_DIR")}"

log() {
  printf '[%s] %s\n' "$(date '+%F %T')" "$*"
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

copy_if_exists() {
  local source_path="$1"
  local target_dir="$2"

  if [[ -f "$source_path" ]]; then
    cp "$source_path" "$target_dir/"
  else
    log "Skipping missing file: $source_path"
  fi
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

read_env_value() {
  local key="$1"

  awk -v key="$key" '
    /^[[:space:]]*#/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      pattern = "^[[:space:]]*" key "[[:space:]]*="
      if (line ~ pattern) {
        sub(pattern, "", line)
        sub(/^[[:space:]]+/, "", line)
        sub(/[[:space:]]+$/, "", line)
        if ((line ~ /^".*"$/) || (line ~ /^'\''.*'\''$/)) {
          line = substr(line, 2, length(line) - 2)
        }
        print line
        exit
      }
    }
  ' "$ENV_FILE"
}

should_show_progress() {
  case "$SHOW_PROGRESS" in
    true)
      return 0
      ;;
    false)
      return 1
      ;;
    auto)
      command -v pv >/dev/null 2>&1 && [[ -t 2 ]]
      ;;
    *)
      die "SHOW_PROGRESS must be one of: auto, true, false"
      ;;
  esac
}

get_database_size_bytes() {
  local database_name="$1"
  local escaped_database_name="${database_name//\'/\'\'}"

  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -tA \
      -c "SELECT pg_database_size('$escaped_database_name');" 2>/dev/null | tr -d '[:space:]'
}

dump_globals() {
  local output_name="globals.sql.gz"
  local output_path="$TARGET/$output_name"

  log "Dumping PostgreSQL globals"
  "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
    pg_dumpall --globals-only -U "$POSTGRES_USER" | gzip -c > "$output_path"

  [[ -s "$output_path" ]] || die "Dump is empty: $output_name"
  gzip -t "$output_path" || die "Integrity check failed for $output_name"
  log "Verified $output_name"
}

dump_database() {
  local database_name="$1"
  local output_name="$2"
  local output_path="$TARGET/$output_name"
  local estimated_size=""
  local pg_dump_args=(pg_dump --no-owner --no-privileges -U "$POSTGRES_USER" "$database_name")

  log "Dumping database '$database_name'"

  if should_show_progress; then
    estimated_size="$(get_database_size_bytes "$database_name")"
    if [[ "$estimated_size" =~ ^[0-9]+$ ]] && [[ "$estimated_size" -gt 0 ]]; then
      log "Showing approximate dump progress for '$database_name'"
      "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
        "${pg_dump_args[@]}" | pv -s "$estimated_size" | gzip -c > "$output_path"
    else
      log "Showing dump throughput for '$database_name' (database size estimate unavailable)"
      "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
        "${pg_dump_args[@]}" | pv | gzip -c > "$output_path"
    fi
  else
    "${DOCKER_COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
      "${pg_dump_args[@]}" | gzip -c > "$output_path"
  fi

  [[ -s "$output_path" ]] || die "Dump is empty: $output_name"
  gzip -t "$output_path" || die "Integrity check failed for $output_name"
  log "Verified $output_name"
}

backup_nats_volume() {
  local volume_name="${COMPOSE_PROJECT_NAME}_nats-data"

  if ! docker volume inspect "$volume_name" >/dev/null 2>&1; then
    log "Skipping missing Docker volume: $volume_name"
    return
  fi

  log "Archiving NATS volume '$volume_name'"
  docker run --rm \
    -v "$volume_name":/source:ro \
    -v "$TARGET":/backup \
    alpine \
    tar czf /backup/nats-volume.tar.gz -C /source .
}

[[ -d "$DEPLOY_DIR" ]] || die "Deployment directory does not exist: $DEPLOY_DIR"
[[ -f "$ENV_FILE" ]] || die "Environment file does not exist: $ENV_FILE"

COMPOSE_FILE="$(resolve_compose_file)"
set_compose_cmd

POSTGRES_USER="$(read_env_value POSTGRES_USER)"
POSTGRES_DB="$(read_env_value POSTGRES_DB)"

[[ -n "${POSTGRES_USER:-}" ]] || die "POSTGRES_USER is not set in $ENV_FILE"
[[ -n "${POSTGRES_DB:-}" ]] || die "POSTGRES_DB is not set in $ENV_FILE"

mkdir -p "$BACKUP_BASE"
STAMP="$(date +%F_%H%M%S)"
TARGET="$BACKUP_BASE/$STAMP"
mkdir -p "$TARGET"

log "Writing backup to $TARGET"

copy_if_exists "$ENV_FILE" "$TARGET"
copy_if_exists "$DEPLOY_DIR/heliosapp.converted_key_pkcs8.pem" "$TARGET"
copy_if_exists "$DEPLOY_DIR/helios-realm.json" "$TARGET"
copy_if_exists "$COMPOSE_FILE" "$TARGET"
copy_if_exists "$NGINX_CONFIG_PATH" "$TARGET"

cat > "$TARGET/metadata.txt" <<EOF
created_at=$(date '+%Y-%m-%dT%H:%M:%S%z')
hostname=$(hostname)
deploy_dir=$DEPLOY_DIR
compose_file=$COMPOSE_FILE
compose_project_name=$COMPOSE_PROJECT_NAME
EOF

dump_globals
dump_database "$POSTGRES_DB" "app.sql.gz"
dump_database "keycloak" "keycloak.sql.gz"

if [[ "$INCLUDE_NATS_VOLUME" == "true" ]]; then
  backup_nats_volume
fi

find "$BACKUP_BASE" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -exec rm -rf {} +

log "Backup completed"

#!/usr/bin/env bash
# Safe restore script that restores a SQL/gz backup into a target schema.
# Refuses to overwrite live 'pg_management' unless FORCE_LIVE_OVERWRITE=YES is explicitly provided.
set -euo pipefail

BACKUP_FILE="${1:-}"
TARGET_SCHEMA="${2:-pg_management_restore_verify}"
CONTAINER_NAME="${MYSQL_CONTAINER:-upcraft-pg-mysql}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD:-}}"

if [[ -z "${BACKUP_FILE}" || ! -f "${BACKUP_FILE}" ]]; then
  echo "Usage: $0 <backup.sql|backup.sql.gz> [target_schema]" >&2
  exit 1
fi

if [[ -z "${DB_PASS}" ]]; then
  echo "ERROR: Set MYSQL_ROOT_PASSWORD or MYSQL_PASSWORD." >&2
  exit 1
fi

if [[ "${TARGET_SCHEMA}" == "pg_management" && "${FORCE_LIVE_OVERWRITE:-NO}" != "YES" ]]; then
  echo "SAFETY BLOCK: Refusing to overwrite live 'pg_management' without FORCE_LIVE_OVERWRITE=YES." >&2
  echo "Pass an isolated target schema such as 'pg_management_restore_verify'." >&2
  exit 1
fi

run_mysql() {
  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
    docker exec -i -e MYSQL_PWD="${DB_PASS}" "${CONTAINER_NAME}" mysql -u "${DB_USER}" "$@"
  else
    MYSQL_PWD="${DB_PASS}" mysql -h "${DB_HOST:-127.0.0.1}" -P "${DB_PORT:-3306}" -u "${DB_USER}" "$@"
  fi
}

echo "Creating target schema '${TARGET_SCHEMA}' if absent..."
run_mysql -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_SCHEMA}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "Restoring '${BACKUP_FILE}' -> '${TARGET_SCHEMA}'..."
if [[ "${BACKUP_FILE}" == *.gz ]]; then
  gzip -dc "${BACKUP_FILE}" | run_mysql "${TARGET_SCHEMA}"
else
  run_mysql "${TARGET_SCHEMA}" < "${BACKUP_FILE}"
fi

echo "Verifying restored table count in '${TARGET_SCHEMA}'..."
run_mysql -N -e "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = '${TARGET_SCHEMA}';"
run_mysql -N -e "SELECT COUNT(*) AS flyway_rows FROM \`${TARGET_SCHEMA}\`.flyway_schema_history WHERE success = 1;"
echo "Restore & verification finished safely into '${TARGET_SCHEMA}'."


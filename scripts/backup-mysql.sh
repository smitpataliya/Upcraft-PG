#!/usr/bin/env bash
# Creates a consistent, point-in-time gzipped backup of the configured MySQL database.
# Never hardcodes credentials; reads MYSQL_CONTAINER / DB_* / MYSQL_* env vars.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
CONTAINER_NAME="${MYSQL_CONTAINER:-upcraft-pg-mysql}"
DB_NAME="${MYSQL_DATABASE:-pg_management}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

if [[ -z "${DB_PASS}" ]]; then
  echo "ERROR: Set MYSQL_PASSWORD or MYSQL_ROOT_PASSWORD before running backup." >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql.gz"

echo "Starting backup of schema '${DB_NAME}' -> '${OUT_FILE}' ..."
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  docker exec -e MYSQL_PWD="${DB_PASS}" "${CONTAINER_NAME}" \
    mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF \
    -u "${DB_USER}" "${DB_NAME}" | gzip -c > "${OUT_FILE}"
else
  MYSQL_HOST="${DB_HOST:-127.0.0.1}"
  MYSQL_PORT="${DB_PORT:-3306}"
  MYSQL_PWD="${DB_PASS}" mysqldump --single-transaction --routines --triggers --events \
    --set-gtid-purged=OFF -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${DB_USER}" "${DB_NAME}" \
    | gzip -c > "${OUT_FILE}"
fi

if [[ ! -s "${OUT_FILE}" ]]; then
  echo "ERROR: Backup output is empty: ${OUT_FILE}" >&2
  exit 1
fi

find "${BACKUP_DIR}" -type f -name "${DB_NAME}-*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete || true
echo "Backup completed successfully: ${OUT_FILE}"


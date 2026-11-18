#!/usr/bin/env bash
# Copies the data (not the schema) from your LOCAL LimitGuard database into
# database/docker-init/02-demo-data.sql, so Docker starts with exactly what you see locally.
#
# Usage (from the project root):
#   PGPASSWORD=your-local-password scripts/export-docker-seed.sh
#   docker compose down -v && docker compose up --build -d
#
# Optional overrides: PGHOST (default 127.0.0.1), PGPORT (5432), PGUSER (postgres), PGDATABASE (limitguard)
set -euo pipefail

cd "$(dirname "$0")/.."
OUT=database/docker-init/02-demo-data.sql
TMP="$OUT.tmp"

export PGHOST="${PGHOST:-127.0.0.1}" PGPORT="${PGPORT:-5432}" PGUSER="${PGUSER:-postgres}" PGDATABASE="${PGDATABASE:-limitguard}"

{
  echo "-- LimitGuard demo data for Docker, exported from $PGDATABASE@$PGHOST:$PGPORT on $(date -u +%Y-%m-%dT%H:%M:%SZ)."
  echo "-- Loaded automatically on the first start of an empty PostgreSQL volume, after 01-schema.sql."
  pg_dump --data-only --disable-triggers --no-owner --no-privileges --schema=public
} > "$TMP"

mv "$TMP" "$OUT"
echo "Wrote $OUT ($(grep -c '^COPY ' "$OUT") tables). Now run: docker compose down -v && docker compose up --build -d"

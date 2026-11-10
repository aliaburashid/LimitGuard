#!/bin/bash
set -euo pipefail
umask 077

directory=$(cd "$(dirname "$0")" && pwd)
project=$(cd "$directory/../.." && pwd)
approved_backup="$project/backups/citi-panel-pre-reset.OiDgM2cl/limitguard.dump"
approved_token=81c3746fc3fdf107146eec695ef63637
mode=${1:-}
db_user=${CITI_DB_USER:-postgres}
repeat_mode=false
approve_write=false
token=
backup=

case "$mode" in
  --check-initial)
    token=$approved_token
    ;;
  --apply-initial)
    token=$approved_token
    backup=$approved_backup
    approve_write=true
    ;;
  --review-reset)
    repeat_mode=true
    ;;
  --apply-reset)
    repeat_mode=true
    approve_write=true
    token=${2:-}
    backup=${3:-}
    if [[ ! $token =~ ^[0-9a-f]{32}$ || -z $backup ]]; then
      printf 'Supply the reviewed reset scope token and a freshly verified backup archive.\n' >&2
      exit 1
    fi
    ;;
  *)
    printf 'Usage: %s --check-initial | --apply-initial | --review-reset | --apply-reset TOKEN BACKUP\n' "$0" >&2
    exit 1
    ;;
esac

if [[ $approve_write == true ]]; then
  backup_dir=$(dirname "$backup")
  test "$(basename "$backup")" = limitguard.dump
  (cd "$backup_dir" && shasum -a 256 -c SHA256SUMS)
  pg_restore --exit-on-error --file=/dev/null "$backup"
  if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
    printf 'STOP: port 8080 is still listening. Stop Spring Boot before applying the reset.\n' >&2
    exit 1
  fi
fi

# Explicitly address the approved local server and use the standard local password prompt.
unset PGSERVICE PGSERVICEFILE PGOPTIONS PGHOSTADDR
export PGCONNECT_TIMEOUT=5
psql -X -W -h 127.0.0.1 -p 5432 -U "$db_user" -d limitguard \
  -v rehearsal=false -v repeat_mode="$repeat_mode" -v approve_write="$approve_write" \
  -v expected_token="$token" -f "$directory/reset.sql"

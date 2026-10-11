#!/usr/bin/env bash
# Run on Oracle: bash backup.sh /path/to/private-backups
set -euo pipefail
cd /opt/coven-gathering
backup_root="${1:?Pass an absolute backup directory}"
[[ "$backup_root" == /* ]] || { echo 'Use an absolute backup path' >&2; exit 1; }
umask 077
mkdir -p "$backup_root"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
# Pause check-ins while taking the database backup.
docker compose stop backend
trap 'docker compose start backend >/dev/null' EXIT
docker compose exec -T db pg_dump -U coven -d coven -Fc > "$backup_root/coven-$stamp.dump"
echo "Backup completed: $stamp"

#!/usr/bin/env bash
# Run only after V2 has migrated the database and the new backend is healthy.
set -euo pipefail
legacy_volume=coven-gathering_coven-proofs
if docker volume inspect "$legacy_volume" >/dev/null 2>&1; then
  # Docker refuses removal if any container still mounts this volume.
  docker volume rm "$legacy_volume"
fi

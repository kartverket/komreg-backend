#!/usr/bin/env bash
set -euo pipefail

SNAPSHOT_IMAGE="matrikkel-test-db"
CONTAINER="matrikkel-db"

case "${1:-}" in
  save)
    echo "Stopping $CONTAINER..."
    docker stop "$CONTAINER"
    echo "Committing snapshot as $SNAPSHOT_IMAGE..."
    docker commit -m "Lagt til testdata" "$CONTAINER" "$SNAPSHOT_IMAGE"
    docker start "$CONTAINER"
    echo "Done. Snapshot saved as image $SNAPSHOT_IMAGE"
    ;;
  restore)
    if ! docker image inspect "$SNAPSHOT_IMAGE" &>/dev/null; then
      echo "No snapshot image found. Run '$0 save' first (after flyway init/migrate)."
      exit 1
    fi
    echo "Removing $CONTAINER..."
    docker rm -f "$CONTAINER"
    echo "Starting from snapshot..."
    docker run --name "$CONTAINER" -p 1521:1521 -p 5500:5500 -d "$SNAPSHOT_IMAGE"
    echo "Done. Database restored."
    ;;
  *)
    echo "Usage: $0 {save|restore}"
    echo "  save    - Snapshot matrikkel-db (run after flyway init/migrate)"
    echo "  restore - Reset matrikkel-db to the saved snapshot"
    exit 1
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail

echo "Starting matrikkel-db (Oracle XE)..."
if docker ps -a --format '{{.Names}}' | grep -q '^matrikkel-db$'; then
  docker start matrikkel-db
else
  docker run --name matrikkel-db -p 1521:1521 -p 5500:5500 -d nexus.statkart.no:8082/matrikkel-oracle-xe-onedb:latest
fi

echo "Starting komreg-db (PostgreSQL)..."
if docker ps -a --format '{{.Names}}' | grep -q '^komreg-db$'; then
  docker start komreg-db
else
  docker run --name komreg-db -p 5432:5432 \
    -e POSTGRES_DB=komreg-db \
    -e POSTGRES_USER=komreg \
    -e POSTGRES_PASSWORD=passord \
    -d postgres:16
fi

echo "Done. Begge databaser kjører."

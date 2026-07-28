#!/bin/sh
# Cairn backup sidecar — nightly pg_dump + attachment snapshot, with retention.
#
# Runs in a postgres:16-alpine container (same major as the server, so pg_dump is compatible)
# with the attachments volume mounted read-only and a dedicated backups volume for output.
# There is no cron in that image, so this is a plain sleep-until-the-next-slot loop; the
# container's `restart: unless-stopped` policy is the only supervision it needs.
#
# Environment (all set from .env.prod by docker-compose.prod.yml):
#   PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE   standard libpq variables
#   BACKUP_DIR            output root                       (default /backups)
#   ATTACHMENTS_DIR       attachment volume, read-only      (default /attachments)
#   BACKUP_HOUR_UTC       0-23                              (default 3)
#   BACKUP_MINUTE_UTC     0-59                              (default 15)
#   BACKUP_RETENTION_DAYS delete artefacts older than this  (default 14)
#   BACKUP_ON_START       "true" runs one backup at boot    (default false)

set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
ATTACHMENTS_DIR="${ATTACHMENTS_DIR:-/attachments}"
BACKUP_HOUR_UTC="${BACKUP_HOUR_UTC:-3}"
BACKUP_MINUTE_UTC="${BACKUP_MINUTE_UTC:-15}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_ON_START="${BACKUP_ON_START:-false}"

log() { echo "[backup] $(date -u '+%Y-%m-%dT%H:%M:%SZ') $*"; }

run_backup() {
  ts="$(date -u '+%Y%m%dT%H%M%SZ')"
  mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/attachments"

  db_tmp="$BACKUP_DIR/db/.cairn-$ts.dump.partial"
  db_out="$BACKUP_DIR/db/cairn-$ts.dump"
  log "pg_dump ${PGDATABASE:-cairn}@${PGHOST:-postgres} -> $db_out"
  # -Fc = custom format: compressed and restorable with pg_restore (see DEPLOY.md).
  if pg_dump -Fc -f "$db_tmp"; then
    mv "$db_tmp" "$db_out"
    log "database dump ok ($(du -h "$db_out" | cut -f1))"
  else
    rm -f "$db_tmp"
    log "ERROR: pg_dump failed — attachments snapshot skipped for this run"
    return 1
  fi

  at_tmp="$BACKUP_DIR/attachments/.attachments-$ts.tar.gz.partial"
  at_out="$BACKUP_DIR/attachments/attachments-$ts.tar.gz"
  if [ -d "$ATTACHMENTS_DIR" ]; then
    log "snapshotting $ATTACHMENTS_DIR -> $at_out"
    if tar -czf "$at_tmp" -C "$ATTACHMENTS_DIR" .; then
      mv "$at_tmp" "$at_out"
      log "attachment snapshot ok ($(du -h "$at_out" | cut -f1))"
    else
      rm -f "$at_tmp"
      log "ERROR: attachment snapshot failed"
      return 1
    fi
  else
    log "WARN: $ATTACHMENTS_DIR is not mounted — no attachment snapshot"
  fi

  # Retention. Both artefacts of a run share a timestamp, so pruning by age keeps them paired.
  log "pruning artefacts older than ${BACKUP_RETENTION_DAYS}d"
  find "$BACKUP_DIR/db" -type f -name 'cairn-*.dump' -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete || true
  find "$BACKUP_DIR/attachments" -type f -name 'attachments-*.tar.gz' -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete || true
  # Abandoned partials from a killed run.
  find "$BACKUP_DIR" -type f -name '.*.partial' -mtime +1 -delete || true

  log "done: $(ls -1 "$BACKUP_DIR/db" | wc -l) dump(s) retained"
}

# Seconds to wait until the next HH:MM UTC slot, using only `date +%s` (busybox-safe).
seconds_until_slot() {
  now="$(date -u '+%s')"
  now_in_day=$(( now % 86400 ))
  slot=$(( BACKUP_HOUR_UTC * 3600 + BACKUP_MINUTE_UTC * 60 ))
  delta=$(( slot - now_in_day ))
  if [ "$delta" -le 0 ]; then
    delta=$(( delta + 86400 ))
  fi
  echo "$delta"
}

mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/attachments"
log "sidecar started; schedule ${BACKUP_HOUR_UTC}:$(printf '%02d' "$BACKUP_MINUTE_UTC") UTC, retention ${BACKUP_RETENTION_DAYS}d"

if [ "$BACKUP_ON_START" = "true" ]; then
  log "BACKUP_ON_START=true — running an immediate backup"
  run_backup || log "initial backup failed (continuing to the schedule)"
fi

while true; do
  wait_s="$(seconds_until_slot)"
  log "next run in ${wait_s}s"
  sleep "$wait_s"
  run_backup || log "scheduled backup failed; will retry at the next slot"
  # Guard against re-entering the same minute after a very fast run.
  sleep 61
done

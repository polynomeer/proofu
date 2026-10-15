#!/usr/bin/env bash
# Restores a backup into a NEW database and then proves it is usable: the dump's checksum, the
# row counts from its manifest, the migration the schema is on, and the references that no
# foreign key protects. Prints how long it took, which is the only honest source for the RTO
# claim in docs/architecture/non-functional.md.
#
#   scripts/restore.sh backups/proofu-….dump        # restore + verify into proofu_restore_<ts>
#   scripts/restore.sh --rehearse                   # back up first, then do the above
#   scripts/restore.sh … --into proofu_check        # a name of your own
#   scripts/restore.sh … --boot                     # also start the api against the restored copy
#   scripts/restore.sh … --keep                     # leave the restored database behind
#   scripts/restore.sh … --into proofu --force      # over the live database (asks first)
#
# The restored database is dropped at the end unless --keep is given, so a rehearsal leaves no
# second copy of everyone's data lying around.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

DUMP=
INTO=
KEEP=
FORCE=
REHEARSE=
BOOT=
while [ $# -gt 0 ]; do
  case "$1" in
    --rehearse) REHEARSE=1; shift ;;
    --into) INTO="${2:-}"; shift 2 ;;
    --into=*) INTO="${1#*=}"; shift ;;
    --keep) KEEP=1; shift ;;
    --boot) BOOT=1; shift ;;
    --force) FORCE=1; shift ;;
    -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "unknown option: $1 (try --help)" ;;
    *) DUMP="$1"; shift ;;
  esac
done

LOG_DIR="$REPO_ROOT/logs/restore"
mkdir -p "$LOG_DIR"
db_describe
step "source"
ok "$DB_DESCRIPTION"

if [ -n "$REHEARSE" ] && [ -z "$DUMP" ]; then
  scripts/backup.sh >"$LOG_DIR/backup.log" 2>&1 ||
    die "the rehearsal's backup failed; see $LOG_DIR/backup.log"
  DUMP="$(ls -t "$REPO_ROOT"/backups/*.dump | head -1)"
  ok "fresh backup: $(basename "$DUMP")"
fi
[ -n "$DUMP" ] || die "give a dump file, or --rehearse to take one first"
[ -f "$DUMP" ] || die "no such dump: $DUMP"
MANIFEST="$DUMP.manifest.json"
LIVE="${PGDATABASE:-proofu}"
: "${INTO:=proofu_restore_$(date +%Y%m%d%H%M%S)}"

step "the file itself"
if [ -f "$DUMP.sha256" ]; then
  expected="$(cut -d' ' -f1 <"$DUMP.sha256")"
  actual="$(shasum -a 256 "$DUMP" | cut -d' ' -f1)"
  [ "$expected" = "$actual" ] || die "checksum mismatch: the backup is not the file that was taken"
  ok "sha256 matches the one recorded at backup time"
else
  warn "no $DUMP.sha256 beside the dump; cannot tell whether the file is intact"
fi

if [ "$INTO" = "$LIVE" ]; then
  [ -n "$FORCE" ] || die "restoring over '$LIVE' needs --force (and it replaces every row)"
  warn "this replaces the contents of the live database '$LIVE'"
  printf '  type the database name to confirm: '
  read -r answer </dev/tty || answer=
  [ "$answer" = "$LIVE" ] || die "cancelled"
fi

restored=
api_pid=
cleanup() {
  [ -n "$api_pid" ] && kill_tree_hard "$api_pid" 10
  if [ -n "$restored" ] && [ -z "$KEEP" ] && [ "$INTO" != "$LIVE" ]; then
    db_exec dropdb --if-exists "$INTO" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

step "restore into $INTO"
started=$(date +%s)
if [ "$INTO" != "$LIVE" ]; then
  db_exec dropdb --if-exists "$INTO" >/dev/null 2>&1 || true
  db_exec createdb "$INTO" >/dev/null || die "could not create the database '$INTO'"
fi
restored=1
# --clean on the live database, plain restore into a fresh one. Errors are collected rather
# than ignored: a restore that printed warnings is not a restore that worked.
restore_args=(--no-owner --no-privileges --dbname "$INTO")
[ "$INTO" = "$LIVE" ] && restore_args+=(--clean --if-exists)
db_exec pg_restore "${restore_args[@]}" <"$DUMP" >"$LOG_DIR/pg_restore.log" 2>&1 || {
  tail -n 20 "$LOG_DIR/pg_restore.log" | sed 's/^/    /'
  die "pg_restore failed; full output in $LOG_DIR/pg_restore.log"
}
elapsed=$(( $(date +%s) - started ))
ok "restored in ${elapsed}s"

# Everything below reads the restored database, not the live one.
PGDATABASE="$INTO"
export PGDATABASE

FAILED=0
check() { # check <what> <expected> <actual>
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1: expected $2, got $3"; FAILED=$((FAILED + 1)); fi
}

step "contents"
if [ -f "$MANIFEST" ]; then
  counts="$(db_table_counts)"
  mismatches="$(python3 -c '
import json, sys
manifest = json.load(open(sys.argv[1]))["tables"]
actual = {}
for line in sys.stdin.read().splitlines():
    if line.strip():
        name, count = line.rsplit("|", 1)
        actual[name] = int(count)
for name in sorted(set(manifest) | set(actual)):
    want, got = manifest.get(name), actual.get(name)
    if want != got:
        print(f"{name}: backup had {want}, restore has {got}")
' "$MANIFEST" <<<"$counts")"
  if [ -n "$mismatches" ]; then
    echo "$mismatches" | sed 's/^/    /'
    bad "the restored rows are not the rows that were backed up"
    FAILED=$((FAILED + 1))
  else
    rows="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["rows"])' "$MANIFEST")"
    ok "every table matches the manifest ($rows rows)"
  fi
  want_schema="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["schemaVersion"])' "$MANIFEST")"
  got_schema="$(db_query "select case when to_regclass('public.flyway_schema_history') is null then 'none' else coalesce((select version from flyway_schema_history where success order by installed_rank desc limit 1), 'none') end")"
  check "schema is on the same migration (V$want_schema)" "$want_schema" "$got_schema"
else
  warn "no manifest beside the dump; row counts cannot be verified"
fi

step "references no foreign key protects"
# Foreign keys are restored with the schema, so they prove themselves. These do not have one:
#
#   exports.object_key -> export_files   a string, by design (ADR-0009 §4)
#   provenance_links.source_id -> whichever table source_type names (polymorphic)
#
# They are also the two that matter most: a document you cannot download and a sentence whose
# source is gone are exactly what this product promises not to have.
check "every READY export still has its file" 0 \
  "$(db_query "select count(*) from exports where status = 'READY' and object_key is not null and object_key not in (select object_key from export_files)")"
check "every READY account export still has its file" 0 \
  "$(db_query "select count(*) from account_exports where status = 'READY' and object_key is not null and object_key not in (select object_key from export_files)")"
# The other direction is the retention promise (docs/data/retention.md): an expired export's
# bytes are gone, unless a live export of the same version/format still shares that file.
check "expired exports kept no bytes behind" 0 \
  "$(db_query "
    select count(*) from export_files f
    where exists (select 1 from exports e where e.object_key = f.object_key and e.status = 'EXPIRED')
      and not exists (select 1 from exports e where e.object_key = f.object_key and e.status <> 'EXPIRED')
      and not exists (select 1 from account_exports a where a.object_key = f.object_key and a.status <> 'EXPIRED')")"
check "no file belongs to nothing" 0 \
  "$(db_query "
    select count(*) from export_files f
    where not exists (select 1 from exports e where e.object_key = f.object_key)
      and not exists (select 1 from account_exports a where a.object_key = f.object_key)")"
check "every provenance link still finds its source" 0 \
  "$(db_query "
    select count(*) from provenance_links p where
         (p.source_type = 'CAREER_ENTRY' and not exists (select 1 from career_entries t where t.id = p.source_id))
      or (p.source_type = 'PROJECT'      and not exists (select 1 from projects t       where t.id = p.source_id))
      or (p.source_type = 'ACHIEVEMENT'  and not exists (select 1 from achievements t   where t.id = p.source_id))
      or (p.source_type = 'CLAIM'        and not exists (select 1 from claims t         where t.id = p.source_id))
      or (p.source_type = 'EVIDENCE'     and not exists (select 1 from evidence t       where t.id = p.source_id))
      or (p.source_type = 'REQUIREMENT'  and not exists (select 1 from requirements t   where t.id = p.source_id))")"

if [ -n "$BOOT" ]; then
  step "the application on the restored copy"
  # The last thing a restore has to survive is the app's own opinion of the schema: Flyway
  # validates the history and Hibernate validates every mapping (ddl-auto=validate). A dump
  # that restores but cannot be served is not a backup.
  jar="$(ls "$REPO_ROOT"/apps/api/build/libs/api-*[!plain].jar 2>/dev/null | head -1)"
  [ -n "$jar" ] || die "no api jar to start; run ./gradlew :api:bootJar first"
  if [ "${DB_MODE:-}" = compose ]; then
    boot_url="jdbc:postgresql://localhost:$DB_PORT/$INTO"
  else
    boot_url="jdbc:postgresql://$PGHOST:${PGPORT:-5432}/$INTO"
  fi
  reserve_port 8080
  boot_started=$(date +%s)
  env AUTH_MODE=header AI_PROVIDER=fake \
    DATABASE_URL="$boot_url" \
    DATABASE_USERNAME="${PGUSER:-proofu}" DATABASE_PASSWORD="${PGPASSWORD:-proofu}" \
    java -jar "$jar" --server.port="$REPLY" >"$LOG_DIR/api.log" 2>&1 &
  api_pid=$!
  quiet_job "$api_pid"
  if wait_http "http://localhost:$REPLY/actuator/health/readiness" 120 "$api_pid"; then
    ok "the api is ready on the restored data ($(( $(date +%s) - boot_started ))s)"
    rows="$(curl -sf "http://localhost:$REPLY/api/v1/career-entries" \
      -H "X-Workspace-Id: 00000000-0000-7000-8000-000000000002" |
      python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("items", [])))' 2>/dev/null || echo "?")"
    ok "it answers queries on the restored data (career entries visible: $rows)"
  else
    grep -E "ERROR|Caused by" "$LOG_DIR/api.log" | head -5 | sed 's/^/    /'
    bad "the api could not start on the restored database; see $LOG_DIR/api.log"
    FAILED=$((FAILED + 1))
  fi
  kill_tree_hard "$api_pid" 10
  api_pid=
fi

printf '\n'
if [ "$FAILED" -gt 0 ]; then
  bad "$FAILED check(s) failed — this backup would not have restored the product"
  exit 1
fi
ok "restore verified in ${elapsed}s$([ -n "$KEEP" ] && echo " (kept as $INTO)" || echo " (restored copy dropped)")"
note "record the numbers in docs/operations/backup-recovery.md if this was the quarterly rehearsal"

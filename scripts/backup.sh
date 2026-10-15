#!/usr/bin/env bash
# Takes a verifiable backup of the database: a custom-format pg_dump, its sha256, and a
# manifest saying what was in it (row counts per table, the migration the schema was on, the
# server version). The manifest is what makes a restore checkable — without it, "the restore
# worked" means only "pg_restore exited 0".
#
#   scripts/backup.sh                    # the local compose postgres -> backups/
#   scripts/backup.sh --out /tmp/x.dump
#   PGHOST=… PGPORT=… PGUSER=… PGPASSWORD=… PGDATABASE=… scripts/backup.sh   # any other server
#
# RPO/RTO targets and the rehearsal live in docs/operations/backup-recovery.md. This script
# never encrypts: at-rest encryption belongs to wherever the file is kept, and the cloud is an
# open decision. Treat the file as personal data — it holds every workspace's rows.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

OUT=
while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:-}"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

started=$(date +%s)
: "${OUT:=$REPO_ROOT/backups/proofu-$(date +%Y%m%d-%H%M%S).dump}"
mkdir -p "$(dirname "$OUT")"

step "source"
db_describe
ok "$DB_DESCRIPTION"

step "dump"
# -Fc (custom): compressed, and pg_restore can rebuild into a differently named database.
db_exec pg_dump -Fc --no-owner --no-privileges >"$OUT"
bytes=$(wc -c <"$OUT" | tr -d ' ')
[ "$bytes" -gt 0 ] || die "pg_dump produced an empty file"
sha="$(shasum -a 256 "$OUT" | cut -d' ' -f1)"
echo "$sha  $(basename "$OUT")" >"$OUT.sha256"
ok "$(awk -v b="$bytes" 'BEGIN { printf "%.1f MB", b / 1048576 }') -> $OUT"

step "manifest"
MANIFEST_PY='
import datetime, json, sys
manifest_path, sha, size, schema_version, server_version = sys.argv[1:6]
tables = {}
for line in sys.stdin.read().splitlines():
    if not line.strip():
        continue
    name, count = line.rsplit("|", 1)
    tables[name] = int(count)
json.dump(
    {
        "takenAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "sha256": sha,
        "sizeBytes": int(size),
        "schemaVersion": schema_version,
        "serverVersion": server_version,
        "tables": tables,
        "rows": sum(tables.values()),
    },
    open(manifest_path, "w"),
    indent=2,
    sort_keys=True,
)
print(f"  {len(tables)} tables, {sum(tables.values())} rows, schema V{schema_version}")
'

counts="$(db_table_counts)"
# By installed_rank, not max(version): the column is text, where '9' sorts after '17'.
schema_version="$(db_query "select case when to_regclass('public.flyway_schema_history') is null then 'none' else coalesce((select version from flyway_schema_history where success order by installed_rank desc limit 1), 'none') end")"
server_version="$(db_query "select current_setting('server_version')")"
python3 -c "$MANIFEST_PY" "$OUT.manifest.json" "$sha" "$bytes" "$schema_version" "$server_version" <<<"$counts"
ok "manifest written ($(( $(date +%s) - started ))s total)"
printf '\n  %s\n  %s\n  %s\n\n' "$OUT" "$OUT.sha256" "$OUT.manifest.json"
note "restore and verify it with: scripts/restore.sh $OUT"

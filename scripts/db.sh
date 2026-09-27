#!/usr/bin/env bash
# The local Postgres, whatever host port it ended up on (scripts/dev.sh moves it when 5432 is
# taken, so a hard-coded psql line stops working).
#
#   scripts/db.sh psql [args…]   # interactive shell inside the container
#   scripts/db.sh url            # the JDBC url the api and worker would use
#   scripts/db.sh port           # just the host port
#   scripts/db.sh tables         # row counts per table
#   scripts/db.sh dump [file]    # pg_dump to logs/db/<timestamp>.sql (or <file>)
#   scripts/db.sh reset          # DELETE ALL LOCAL DATA and let the api migrate again (asks first)
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"
cd "$REPO_ROOT"

cmd="${1:-}"; [ $# -gt 0 ] && shift || true
[ -n "$cmd" ] || { sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

docker_running || die "the docker daemon is down; start it or run scripts/dev.sh"
compose ps --status running --services 2>/dev/null | grep -qx postgres ||
  die "the postgres container is not running; start it with scripts/dev.sh (or docker compose -f infra/docker-compose.yml up -d postgres)"
PG_PORT="$(compose port postgres 5432 2>/dev/null | sed 's/.*://')"

psql_in() { compose exec -T postgres psql -U proofu -d proofu "$@"; }

case "$cmd" in
  port) echo "$PG_PORT" ;;
  url) echo "jdbc:postgresql://localhost:$PG_PORT/proofu" ;;
  psql) compose exec postgres psql -U proofu -d proofu "$@" ;;
  tables)
    psql_in -At -c "select relname, n_live_tup from pg_stat_user_tables order by n_live_tup desc, relname" |
      awk -F'|' '{ printf "  %-32s %s\n", $1, $2 }'
    ;;
  dump)
    out="${1:-$REPO_ROOT/logs/db/$(date +%Y%m%d-%H%M%S).sql}"
    mkdir -p "$(dirname "$out")"
    compose exec -T postgres pg_dump -U proofu -d proofu >"$out"
    ok "dumped to $out ($(wc -c <"$out" | tr -d ' ') bytes)"
    ;;
  reset)
    yes=
    [ "${1:-}" = "--yes" ] && yes=1
    warn "this drops every table in the local proofu database (synthetic data only, but it is gone)"
    if [ -z "$yes" ]; then
      printf '  type "reset" to confirm: '
      read -r answer </dev/tty || answer=
      [ "$answer" = reset ] || die "cancelled"
    fi
    psql_in -q -c 'drop schema public cascade; create schema public;' >/dev/null
    ok "schema dropped; restart the api (scripts/dev.sh) and Flyway migrates from V1"
    ;;
  *) die "unknown command: $cmd (try scripts/db.sh with no arguments)" ;;
esac

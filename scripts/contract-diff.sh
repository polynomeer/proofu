#!/usr/bin/env bash
# Compare the declared OpenAPI contract with the API's runtime document.
#
# Usage: scripts/contract-diff.sh packages/contracts/openapi.yaml runtime-openapi.json
#
# Prints paths+methods present in one document but not the other. Exit 1 when
# the runtime spec lacks anything the contract declares (a client would break).
set -euo pipefail

contract="$1"
runtime="$2"

operations() {
  # yq/jq-free: python is available on all CI images we use.
  python3 - "$1" <<'PY'
import json, sys
try:
    import yaml  # type: ignore
except ImportError:
    yaml = None
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    text = f.read()
doc = json.loads(text) if path.endswith(".json") else (yaml.safe_load(text) if yaml else json.loads(text))
for p, item in sorted((doc.get("paths") or {}).items()):
    for m in sorted(item):
        if m.lower() in ("get", "post", "put", "patch", "delete"):
            print(f"{m.upper()} {p}")
PY
}

declared=$(operations "$contract")
implemented=$(operations "$runtime")

missing=$(comm -23 <(echo "$declared") <(echo "$implemented"))
extra=$(comm -13 <(echo "$declared") <(echo "$implemented"))

if [[ -n "$missing" ]]; then
  echo "Declared in contract but not implemented:"
  echo "$missing" | sed 's/^/  - /'
fi
if [[ -n "$extra" ]]; then
  echo "Implemented but not in contract:"
  echo "$extra" | sed 's/^/  - /'
fi

[[ -z "$missing" ]]

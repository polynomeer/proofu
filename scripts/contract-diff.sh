#!/usr/bin/env bash
# Compare the declared OpenAPI contract with the API's runtime document.
#
# Usage: scripts/contract-diff.sh packages/contracts/openapi.yaml runtime-openapi.json
#
# Prints operations (METHOD path) present in one document but not the other. Paths are compared
# without the server base path (the contract declares `servers: /api/v1`, the runtime document
# spells it into every path) and without parameter names, so `{id}` and `{applicationId}` are
# the same operation.
#
# Exits 1 on any difference: a generated client would call an endpoint that does not exist, and
# an endpoint the contract never declared is one nobody reviewed.
set -euo pipefail

contract="$1"
runtime="$2"

# Emits "METHOD /path" lines. YAML goes through redocly (a contracts dev dependency) first,
# so nothing here depends on a Python YAML module being present.
normalize='import json,re,sys
API_PREFIX = "/api/v1"
d = json.load(open(sys.argv[1]))
server = ((d.get("servers") or [{}])[0].get("url") or "").rstrip("/")
base = re.sub(r"^https?://[^/]+", "", server)
for path, item in sorted((d.get("paths") or {}).items()):
    shape = path[len(base):] if base and path.startswith(base) else path
    # springdoc writes the whole path; the contract keeps the prefix in `servers`.
    if shape.startswith(API_PREFIX):
        shape = shape[len(API_PREFIX):]
    shape = re.sub(r"\{[^}]+\}", "{}", shape)
    for method in sorted(item):
        if method.lower() in ("get", "post", "put", "patch", "delete"):
            print(method.upper(), shape)'

operations() {
  local file="$1"
  if [[ "$file" != *.json ]]; then
    local bundled
    bundled="$(mktemp -t contract-bundle-XXXXXX).json"
    pnpm --filter contracts exec redocly bundle "$(cd "$(dirname "$file")" && pwd)/$(basename "$file")" \
      --ext json -o "$bundled" >/dev/null
    file="$bundled"
  fi
  python3 -c "$normalize" "$file"
}

declared=$(operations "$contract" | sort)
implemented=$(operations "$runtime" | sort)

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
[[ -z "$missing" && -z "$extra" ]] && echo "Contract and runtime spec declare the same operations."

[[ -z "$missing" && -z "$extra" ]]

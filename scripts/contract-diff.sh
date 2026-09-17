#!/usr/bin/env bash
# Compare the declared OpenAPI contract with the API's runtime document.
#
# Usage: scripts/contract-diff.sh packages/contracts/openapi.yaml runtime-openapi.json
#
# Prints operations (METHOD path) present in one document but not the other.
# Exits 1 when the runtime spec lacks anything the contract declares, because a
# generated client would call an endpoint that does not exist.
set -euo pipefail

contract="$1"
runtime="$2"

# Emits "METHOD /path" lines. YAML is read with PyYAML when available, else Ruby's stdlib.
operations() {
  local file="$1"
  if [[ "$file" == *.json ]]; then
    python3 -c 'import json,sys
d=json.load(open(sys.argv[1]))
for p,i in sorted((d.get("paths") or {}).items()):
    for m in sorted(i):
        if m.lower() in ("get","post","put","patch","delete"): print(m.upper(), p)' "$file"
  elif python3 -c 'import yaml' 2>/dev/null; then
    python3 -c 'import yaml,sys
d=yaml.safe_load(open(sys.argv[1]))
for p,i in sorted((d.get("paths") or {}).items()):
    for m in sorted(i):
        if m.lower() in ("get","post","put","patch","delete"): print(m.upper(), p)' "$file"
  else
    ruby -ryaml -e 'd=YAML.safe_load(File.read(ARGV[0]))
(d["paths"]||{}).sort.each{|p,i| i.keys.sort.each{|m| puts "#{m.upcase} #{p}" if %w[get post put patch delete].include?(m.downcase)}}' "$file"
  fi
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

[[ -z "$missing" ]]

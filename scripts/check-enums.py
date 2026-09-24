#!/usr/bin/env python3
"""Check that one enum reads the same in the domain, the database and the contract.

CLAUDE.md requires an enum's values to match in four places: the Kotlin enum in
packages/domain, the CHECK constraint in migrations/, the schema in packages/contracts and the
prose in docs/domain. The first three are machine-readable and checked here; the prose is not.

Usage: scripts/check-enums.py [bundled-contract.json]
       (without an argument the contract is bundled with redocly on the fly)
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (table, column) -> who owns the vocabulary. Every enum-shaped CHECK in migrations/ must appear
# here, so a new one cannot slip in without someone deciding where its values are declared:
#   ("kotlin", Name)   a domain or gateway enum
#   ("contract", Name) a schema in openapi.yaml, for vocabularies with no Kotlin type
#   ("literal", [...]) the database is the only place it exists; changing it means editing here
#   None               not an enum (a range check, a role we have not modelled)
COLUMN_ENUM = {
    ("workspace_members", "role"): ("literal", ["OWNER"]),  # membership roles are not a domain enum yet
    ("career_entries", "type"): ("kotlin", "CareerEntryType"),
    ("career_entries", "visibility"): ("kotlin", "Visibility"),
    ("career_entries", "status"): ("kotlin", "CareerEntryStatus"),
    ("projects", "visibility"): ("kotlin", "Visibility"),
    ("achievements", "confidence"): None,  # numeric range, not a vocabulary  # numeric column; the CHECK is a range, not an enum
    ("skills", "proficiency"): ("kotlin", "ProficiencyLevel"),
    ("skills", "category"): ("kotlin", "SkillCategory"),
    ("capabilities", "category"): ("kotlin", "CapabilityCategory"),
    ("capabilities", "self_assessed_level"): ("kotlin", "ProficiencyLevel"),
    ("capabilities", "evidence_based_level"): ("kotlin", "ProficiencyLevel"),
    ("claims", "claim_type"): ("kotlin", "ClaimType"),
    ("claims", "sensitivity"): ("kotlin", "Sensitivity"),
    ("claim_sources", "source_type"): ("kotlin", "ClaimSourceType"),
    ("evidence", "type"): ("kotlin", "EvidenceType"),
    ("evidence", "source"): ("kotlin", "EvidenceSource"),
    ("evidence", "verification"): ("kotlin", "VerificationStatus"),
    ("evidence", "sensitivity"): ("kotlin", "Sensitivity"),
    ("claim_evidence", "relation"): ("kotlin", "EvidenceRelation"),
    ("job_posting_snapshots", "source"): ("kotlin", "SnapshotSource"),
    ("requirements", "category"): ("kotlin", "RequirementCategory"),
    ("requirements", "status"): ("kotlin", "RequirementStatus"),
    ("requirements", "origin"): ("kotlin", "RequirementOrigin"),
    ("applications", "status"): ("kotlin", "ApplicationStatus"),
    ("application_status_events", "from_status"): ("kotlin", "ApplicationStatus"),
    ("application_status_events", "to_status"): ("kotlin", "ApplicationStatus"),
    ("documents", "type"): ("kotlin", "DocumentType"),
    ("document_versions", "created_by"): ("kotlin", "VersionAuthor"),
    ("provenance_links", "source_type"): ("kotlin", "ProvenanceSourceType"),
    ("provenance_links", "relation"): ("kotlin", "ProvenanceRelation"),
    ("reviews", "confidence"): ("kotlin", "ReviewConfidence"),
    ("requirement_matches", "band"): ("kotlin", "ScoreBand"),
    ("requirement_matches", "claim_status_at_scoring"): ("kotlin", "ClaimStatus"),
    ("requirement_matches", "user_decision"): ("kotlin", "MatchDecision"),
    ("jobs", "status"): ("contract", "JobStatus"),
    ("exports", "format"): ("kotlin", "ExportFormat"),
    ("exports", "status"): ("kotlin", "ExportStatus"),
    ("ai_executions", "purpose"): ("kotlin", "AiPurpose"),
    ("ai_executions", "status"): ("kotlin", "AiExecutionStatus"),
    ("audit_events", "actor_type"): ("literal", ["USER", "SYSTEM", "OPERATOR"]),
    ("workspace_settings", "ai_consent"): ("kotlin", "AiConsent"),
    ("workspace_settings", "default_visibility"): ("kotlin", "Visibility"),
    ("account_exports", "status"): ("contract", "AccountExportStatus"),
    ("user_profiles", "links"): None,
    ("interview_handoffs", "status"): ("literal", ["REQUESTED", "SENT", "FAILED", "CANCELLED"]),
}

VALUE = re.compile(r"'([A-Za-z_][A-Za-z0-9_]*)'")


def kotlin_enums() -> dict[str, list[str]]:
    """Enum name -> values, from every Kotlin source that ships domain or gateway types."""
    found: dict[str, list[str]] = {}
    for directory in ("packages/domain/src/main/kotlin", "packages/ai-gateway/src/main/kotlin"):
        for path in (ROOT / directory).rglob("*.kt"):
            text = path.read_text()
            for match in re.finditer(r"enum class (\w+)[^{]*\{(.*?)\n\}", text, re.S):
                values = re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|$)", match.group(2), re.M)
                if values:
                    found[match.group(1)] = values
    return found


def migration_checks() -> dict[tuple[str, str], list[str]]:
    """(table, column) -> the values a CHECK constraint allows, following ALTERs in order."""
    found: dict[tuple[str, str], list[str]] = {}
    files = sorted((ROOT / "migrations").glob("V*.sql"), key=lambda p: int(re.search(r"V(\d+)", p.name).group(1)))
    for path in files:
        sql = path.read_text()
        for table_match in re.finditer(r"CREATE TABLE (\w+)\s*\((.*?)\n\);", sql, re.S | re.I):
            table, body = table_match.group(1), table_match.group(2)
            for column, values in columns_with_check(body):
                found[(table, column)] = values
        for alter in re.finditer(r"ALTER TABLE (\w+)(.*?);", sql, re.S | re.I):
            table, body = alter.group(1), alter.group(2)
            for column, values in columns_with_check(body):
                found[(table, column)] = values
    return found


def columns_with_check(body: str) -> list[tuple[str, list[str]]]:
    """Every `col ... CHECK (col IN ('A', 'B'))` in a table body or an ALTER, however wrapped."""
    out = []
    for match in re.finditer(
        r"CHECK\s*\(\s*(?:(\w+)\s+IS\s+NULL\s+OR\s+)?(\w+)\s+IN\s*\(([^)]*)\)",
        body,
        re.S | re.I,
    ):
        column = match.group(2)
        values = VALUE.findall(match.group(3))
        if values:
            out.append((column, values))
    return out


def contract_enums(path: Path) -> dict[str, list[str]]:
    document = json.loads(path.read_text())
    schemas = (document.get("components") or {}).get("schemas") or {}
    return {name: schema["enum"] for name, schema in schemas.items() if isinstance(schema, dict) and "enum" in schema}


def bundled_contract() -> Path:
    target = Path(tempfile.mkstemp(suffix=".json")[1])
    subprocess.run(
        ["pnpm", "--filter", "contracts", "exec", "redocly", "bundle",
         str(ROOT / "packages/contracts/openapi.yaml"), "--ext", "json", "-o", str(target)],
        cwd=ROOT, check=True, stdout=subprocess.DEVNULL,
    )
    return target


def main() -> int:
    contract_path = Path(sys.argv[1]) if len(sys.argv) > 1 else bundled_contract()
    kotlin, checks, contract = kotlin_enums(), migration_checks(), contract_enums(contract_path)
    problems: list[str] = []

    for (table, column), values in sorted(checks.items()):
        if (table, column) not in COLUMN_ENUM:
            problems.append(
                f"{table}.{column} has an enum CHECK that no entry in COLUMN_ENUM claims: {values}"
            )
            continue
        owner = COLUMN_ENUM[(table, column)]
        if owner is None:
            continue
        kind, reference = owner
        declared = {"kotlin": kotlin, "contract": contract}.get(kind, {}).get(reference) if kind != "literal" else reference
        if declared is None:
            problems.append(f"{table}.{column} points at {kind} {reference}, which does not exist")
            continue
        if sorted(values) != sorted(declared):
            where = reference if kind != "literal" else "the list in this script"
            problems.append(f"{table}.{column} allows {sorted(values)} but {where} declares {sorted(declared)}")

    for name, values in sorted(contract.items()):
        if name in kotlin and values != kotlin[name]:
            problems.append(f"contract {name} is {values} but the Kotlin enum is {kotlin[name]}")

    for problem in problems:
        print(f"  - {problem}")
    if problems:
        print(f"{len(problems)} enum mismatch(es); CLAUDE.md: change one place, change all four.")
        return 1
    print(f"Enums agree: {len(checks)} database columns and {len(set(contract) & set(kotlin))} contract schemas.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

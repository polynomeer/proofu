#!/usr/bin/env python3
"""Every metric an alert rule queries must be one something actually publishes.

A rule that names a series nobody exports is not an alert: it evaluates to nothing, for ever,
and the SLI it was written for is unprotected. Prometheus cannot tell the difference, and
neither can `promtool check rules` — the expression is perfectly valid PromQL.

So: pull the metric names out of infra/observability/alerts.yml, turn each back into the
Micrometer name it would come from (`proofu_ai_cost_usd_total` -> `proofu.ai.cost.usd`) and
find that name in the Kotlin sources. Names that come from somewhere else — Prometheus itself,
Spring Boot's own instrumentation — are listed here with where they come from.

Run by scripts/alerts.sh (and so by CI). Exits 1 on a name nothing backs.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RULES = ROOT / "infra" / "observability" / "alerts.yml"
SOURCES = [ROOT / "apps", ROOT / "packages"]

# Series this repository does not define. Each entry says who does, so an unexplained name
# cannot hide in the list.
PROVIDED_ELSEWHERE = {
    "up": "Prometheus writes it for every scrape",
    "http_server_requests_seconds_count": "Spring Boot WebMVC instrumentation (Micrometer)",
    "http_server_requests_seconds_bucket": "the same timer, bucketed by management.metrics.distribution.slo",
    "http_server_requests_seconds_sum": "Spring Boot WebMVC instrumentation (Micrometer)",
}

# Suffixes Prometheus appends to a Micrometer meter.
SUFFIXES = ("_total", "_count", "_sum", "_bucket", "_max")

# Label selectors are removed first, so `le` and `uri` inside {...} are not mistaken for
# metrics; what is left is a metric name before a range, an operator or the end of a line.
LABELS = re.compile(r"\{[^{}]*\}")
METRIC = re.compile(r"(?<![\w.])([a-zA-Z_][a-zA-Z0-9_]*)(?=\s*[\[]|\s*[<>=!)]|\s*$)", re.MULTILINE)
# Words that look like metrics in an expression but are not.
FUNCTIONS = {
    "sum", "rate", "increase", "max", "min", "avg", "count", "clamp_min", "clamp_max",
    "max_over_time", "min_over_time", "avg_over_time", "histogram_quantile", "by", "without",
    "and", "or", "unless", "on", "ignoring", "group_left", "group_right", "absent", "time",
}


def rule_expressions(text: str) -> list[str]:
    """The `expr:` blocks, scalar or `|` folded, without parsing YAML (no dependency)."""
    out: list[str] = []
    lines = text.splitlines()
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped.startswith("expr:"):
            continue
        rest = stripped[len("expr:"):].strip()
        if rest and rest != "|":
            out.append(rest)
            continue
        indent = len(line) - len(line.lstrip())
        block = []
        for follow in lines[i + 1:]:
            if not follow.strip():
                block.append("")
                continue
            if len(follow) - len(follow.lstrip()) <= indent:
                break
            block.append(follow)
        out.append("\n".join(block))
    return out


def micrometer_name(series: str) -> str:
    for suffix in SUFFIXES:
        if series.endswith(suffix):
            series = series[: -len(suffix)]
            break
    return series.replace("_", ".")


def kotlin_sources() -> list[str]:
    texts = []
    for root in SOURCES:
        for path in root.rglob("*.kt"):
            if "/build/" in str(path) or "/src/test/" in str(path):
                continue
            texts.append(path.read_text(encoding="utf-8"))
    return texts


def main() -> int:
    if not RULES.exists():
        print(f"no rules at {RULES}", file=sys.stderr)
        return 1
    text = RULES.read_text(encoding="utf-8")

    series: set[str] = set()
    for expr in rule_expressions(text):
        for name in METRIC.findall(LABELS.sub("", expr)):
            if name not in FUNCTIONS:
                series.add(name)

    sources = kotlin_sources()
    problems = []
    checked = 0
    for name in sorted(series):
        if name in PROVIDED_ELSEWHERE:
            continue
        meter = micrometer_name(name)
        if any(f'"{meter}"' in source for source in sources):
            checked += 1
            continue
        problems.append(
            f"- {name}: no Kotlin source registers a meter named '{meter}'. "
            f"Rename the rule, or add the name to PROVIDED_ELSEWHERE with who publishes it."
        )

    if problems:
        print("Alert rules query metrics that nothing publishes:", file=sys.stderr)
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(
        f"Alert rules agree with the code: {checked} metric(s) registered here, "
        f"{len(series & set(PROVIDED_ELSEWHERE))} from Prometheus and Spring Boot."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

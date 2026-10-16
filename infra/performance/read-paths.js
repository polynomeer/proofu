// The read paths of the app under load, with the targets of
// docs/architecture/non-functional.md §성능 as thresholds: a normal read answers within 500ms
// at p95, and accepting a generation request within 1s.
//
// Run it with scripts/perf.sh (it seeds a workspace and starts an api). The groups are the
// screens a user actually opens, in the order the web requests them.
import http from "k6/http";
import { check, group } from "k6";

const BASE = __ENV.BASE_URL;
const WORKSPACE = __ENV.WORKSPACE_ID;
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || "30s";

export const options = {
  scenarios: {
    // What someone browsing their own records does: lists, a detail, a search.
    reads: { executor: "constant-vus", vus: VUS, duration: DURATION, exec: "reads" },
    // Starting AI work is rarer and is allowed to be slower; it is a write plus an enqueue.
    enqueue: {
      executor: "constant-arrival-rate",
      rate: 2,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 10,
      exec: "enqueue",
    },
  },
  thresholds: {
    // The SLI itself. k6 exits non-zero when a threshold fails, so this file is the check.
    "http_req_duration{kind:read}": ["p(95)<500"],
    "http_req_duration{kind:enqueue}": ["p(95)<1000"],
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

const read = (name) => ({
  headers: { "X-Workspace-Id": WORKSPACE },
  tags: { kind: "read", path: name },
});

function get(name, path) {
  const response = http.get(`${BASE}${path}`, read(name));
  check(response, { [`${name} 200`]: (r) => r.status === 200 });
  return response;
}

export function reads() {
  group("대시보드", () => get("dashboard", "/api/v1/dashboard"));
  group("목록", () => {
    get("career", "/api/v1/career-entries?limit=20");
    get("evidence", "/api/v1/evidence?limit=20");
    get("postings", "/api/v1/job-postings?limit=20");
    get("applications", "/api/v1/applications");
    get("documents", "/api/v1/documents?limit=20");
    get("skills", "/api/v1/skills?limit=20");
  });
  group("두 번째 페이지", () => {
    const first = get("career", "/api/v1/career-entries?limit=20");
    const cursor = first.json("nextCursor");
    if (cursor) get("career-page-2", `/api/v1/career-entries?limit=20&cursor=${cursor}`);
  });
  // encodeURIComponent, not the raw word: an unencoded Korean query string is rejected by
  // the server before it reaches the app, and the measurement would be of a 400.
  group("검색", () => get("search", `/api/v1/search?q=${encodeURIComponent("코틀린특이값")}`));
  group("상세", () => {
    const list = get("career", "/api/v1/career-entries?limit=1");
    const id = list.json("items.0.id");
    if (id) get("career-detail", `/api/v1/career-entries/${id}`);
  });
}

export function enqueue() {
  const snapshots = http.get(`${BASE}/api/v1/job-postings?limit=1`, read("postings"));
  const postingId = snapshots.json("items.0.id");
  if (!postingId) return;
  const posting = http.get(`${BASE}/api/v1/job-postings/${postingId}`, read("posting"));
  const snapshotId = posting.json("snapshots.0.id") ?? posting.json("latestSnapshot.id");
  if (!snapshotId) return;
  const response = http.post(
    `${BASE}/api/v1/job-posting-snapshots/${snapshotId}/analysis-jobs`,
    null,
    { headers: { "X-Workspace-Id": WORKSPACE }, tags: { kind: "enqueue", path: "analysis" } },
  );
  check(response, { "analysis 202": (r) => r.status === 202 });
}

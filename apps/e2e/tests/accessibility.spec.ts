import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { API, apiAs, created, switchUser, unique } from "./helpers";

/**
 * WCAG 2.2 AA is a release gate (docs/design/accessibility.md); this is its automated half.
 * Screens are seeded with real rows so empty states are not all that gets checked.
 */
test.describe.configure({ mode: "serial" });

const sub = unique("a11y");
let entryId = "";
let projectId = "";
let evidenceId = "";
let applicationId = "";

const WCAG = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"];

async function scan(page: Page, path: string) {
  await page.goto(path);
  // A 404 or an error state would also pass a scan, so check the page really rendered first.
  await expect(page.getByRole("heading").first()).toBeVisible();
  await expect(page.getByText(/불러오지 못했습니다|찾을 수 없습니다/)).toHaveCount(0);
  const results = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(results.passes.length, `${path}: too few rules ran to trust the result`).toBeGreaterThan(
    10,
  );
  return results.violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    nodes: v.nodes.map((n) => n.target.join(" ")).slice(0, 3),
  }));
}

test.beforeAll(async () => {
  await switchUser(sub, "접근성 테스트");
  const api = await apiAs(sub);
  entryId = await created(api, "/career-entries", {
    type: "EMPLOYMENT",
    title: "백엔드 엔지니어",
    organization: "예시 주식회사",
    startDate: "2021-03-01",
  });
  projectId = await created(api, "/projects", {
    careerEntryId: entryId,
    name: "결제 재구축",
    role: "리드",
    summary: "결제 파이프라인을 재구축했습니다.",
    startDate: "2024-01-01",
  });
  const achievement = await created(api, `/projects/${projectId}/achievements`, {
    action: "결제 실패율을 낮추는 재시도 설계",
    outcome: "결제 실패율 2.1%에서 0.6%로 감소",
    metricValue: 0.6,
    metricUnit: "%",
    confidence: 0.9,
  });
  const claim = await created(api, "/claims", {
    text: "결제 파이프라인을 재설계해 실패율을 1.5%p 낮춤",
    type: "FACT",
    sources: [{ type: "ACHIEVEMENT", id: achievement }],
  });
  evidenceId = await created(api, "/evidence", {
    type: "URL",
    title: "결제 지표 대시보드",
    source: "USER_INPUT",
    uri: "https://example.test/payments",
    verification: "USER_VERIFIED",
    capturedAt: "2026-02-01T00:00:00Z",
  });
  await created(api, `/claims/${claim}/evidence`, {
    evidenceId,
    relation: "SUPPORTS",
    confidence: 0.9,
  });
  const skill = await created(api, "/skills", {
    canonicalName: "Kotlin",
    category: "PROGRAMMING_LANGUAGE",
    proficiency: "ADVANCED",
  });
  await api.put(
    `${process.env.E2E_API_URL ?? "http://localhost:8080/api/v1"}/projects/${projectId}/skills`,
    {
      data: { skillIds: [skill] },
    },
  );
  const capability = await created(api, "/capabilities", {
    name: "결제 시스템 설계",
    definition: "실패를 견디는 결제 흐름을 설계한다",
    category: "SYSTEM_DESIGN",
    selfAssessedLevel: "INDEPENDENT",
  });
  await api.put(`${API}/capabilities/${capability}/evidence`, {
    data: { evidenceIds: [evidenceId] },
  });
  const imported = await api.post(`${API}/job-postings/import`, {
    data: {
      source: "MANUAL_TEXT",
      company: "예시 주식회사",
      roleTitle: "백엔드 엔지니어",
      text: "[자격 요건]\n- 백엔드 5년 이상\n- 결제 도메인 경험\n\n[우대 사항]\n- 대용량 트래픽 경험",
    },
  });
  expect(imported.status(), await imported.text()).toBe(201);
  const { snapshotId } = (await imported.json()) as { snapshotId: string };
  applicationId = await created(api, "/applications", { snapshotId });
  await api.dispose();
});

const SCREENS = [
  ["대시보드", "/"],
  ["커리어 목록", "/career"],
  ["기술", "/career/skills"],
  ["Evidence 목록", "/evidence"],
  ["역량", "/evidence/capabilities"],
  ["채용공고 목록", "/jobs"],
  ["지원 문서 목록", "/documents"],
  ["지원 관리", "/applications"],
  ["설정", "/settings"],
  ["도움말", "/help"],
] as const;

for (const [name, path] of SCREENS) {
  test(`접근성: ${name} 화면에 WCAG 2.2 AA 위반이 없다`, async ({ page }) => {
    expect(await scan(page, path)).toEqual([]);
  });
}

test("접근성: 상세·폼 화면도 위반이 없다", async ({ page }) => {
  for (const path of [
    `/career/${entryId}`,
    "/career/new",
    `/projects/${projectId}`,
    `/evidence/${evidenceId}`,
    "/evidence/new",
    "/jobs/new",
    `/applications/${applicationId}`,
  ]) {
    expect(await scan(page, path), path).toEqual([]);
  }
});

test("접근성: 열린 폼과 모바일 폭에서도 위반이 없다", async ({ page }) => {
  // A form that only exists once opened: the fields, not just the button, must pass.
  await page.goto("/career/skills");
  await page.getByRole("button", { name: "기술 추가" }).click();
  await expect(page.getByRole("textbox", { name: /^이름/ })).toBeVisible();
  expect(
    (await new AxeBuilder({ page }).withTags(WCAG).analyze()).violations.map((v) => v.id),
  ).toEqual([]);

  await page.setViewportSize({ width: 390, height: 844 });
  for (const path of ["/", "/career", "/applications"]) {
    expect(await scan(page, path), `mobile ${path}`).toEqual([]);
  }
});

test("접근성: 키보드로 본문까지 건너뛸 수 있고 포커스가 보인다", async ({ page }) => {
  await page.goto("/career");
  await page.keyboard.press("Tab");
  const skip = page.getByRole("link", { name: "본문으로 건너뛰기" });
  await expect(skip).toBeFocused();
  await skip.press("Enter");
  await expect(page.locator("main")).toBeFocused();
});

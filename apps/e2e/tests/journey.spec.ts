import { expect, test, type Page } from "@playwright/test";

import { apiAs, created, switchUser, unique } from "./helpers";

/**
 * docs/testing/e2e-scenarios.md 1–8 with the fake AI provider: the career chain is seeded
 * through the API (the forms are covered by API tests), everything from the posting onward runs
 * in the browser. One user, one journey, in order.
 */
test.describe.configure({ mode: "serial" });

const sub = unique("journey");
let postingUrl = "";
let applicationUrl = "";
let documentUrl = "";
let submissionUrl = "";

const POSTING = `[담당 업무]
- B2B SaaS 제품 로드맵 수립과 우선순위 결정
- 데이터 기반 의사결정과 실험 설계

[자격 요건]
- 프로덕트 매니저 경력 5년 이상
- SaaS 제품 기획 경험

[우대 사항]
- 신규 서비스 런칭 경험`;

test.beforeAll(async () => {
  await switchUser(sub, "여정 테스트");
  const api = await apiAs(sub);
  const entry = await created(api, "/career-entries", {
    type: "EMPLOYMENT",
    title: "프로덕트 매니저",
    organization: "예시 주식회사",
    startDate: "2019-04-01",
  });
  const project = await created(api, "/projects", {
    careerEntryId: entry,
    name: "SaaS 온보딩 재설계",
    role: "PM",
    summary: "온보딩 플로우 축소로 활성화율 개선",
    startDate: "2025-03-01",
    endDate: "2025-09-30",
  });
  const achievement = await created(api, `/projects/${project}/achievements`, {
    action: "SaaS 온보딩 플로우를 5단계에서 2단계로 축소",
    outcome: "가입 후 7일 활성화율 40% 상승",
    metricValue: 40,
    metricUnit: "%",
    confidence: 0.9,
  });
  const claim = await created(api, "/claims", {
    text: "SaaS 제품 기획을 주도해 온보딩을 재설계하고 활성화율을 40% 개선",
    type: "FACT",
    sources: [{ type: "ACHIEVEMENT", id: achievement }],
  });
  const evidence = await created(api, "/evidence", {
    type: "URL",
    title: "Q3 활성화 지표 대시보드",
    uri: "https://example.test/dashboard",
    verification: "USER_VERIFIED",
  });
  await created(api, `/claims/${claim}/evidence`, {
    evidenceId: evidence,
    relation: "SUPPORTS",
    confidence: 0.9,
  });
  await api.dispose();
});

async function waitForJobSummary(page: Page, pattern: RegExp) {
  await expect(page.getByRole("status").filter({ hasText: pattern })).toBeVisible({
    timeout: 60_000,
  });
}

test("2. 공고를 저장하고 요구사항 초안을 추출·승인한다", async ({ page }) => {
  await page.goto("/jobs/new");
  // Labels carry the required marker and help text, so fields are addressed by accessible name.
  await page.getByRole("textbox", { name: "회사", exact: true }).fill("예시 주식회사");
  await page.getByRole("textbox", { name: "직무", exact: true }).fill("B2B SaaS 프로덕트 매니저");
  await page.getByRole("textbox", { name: /^공고 본문/ }).fill(POSTING);
  await page.getByRole("button", { name: "공고 저장" }).click();
  await expect(page).toHaveURL(/\/jobs\/[0-9a-f-]+\?snapshot=/);
  postingUrl = page.url();

  await page.getByRole("button", { name: "요구사항 분석" }).click();
  await waitForJobSummary(page, /추출|초안|건/);
  await expect(page.getByText(/검토 필요 \d+/)).toBeVisible();

  const approve = page.getByRole("button", { name: "승인", exact: true });
  const drafts = await approve.count();
  expect(drafts).toBeGreaterThanOrEqual(3);
  for (let i = 0; i < drafts; i++) {
    await approve.first().click();
    await expect(approve).toHaveCount(drafts - i - 1);
  }
  await expect(page.getByText(new RegExp(`승인 ${drafts} / 전체 ${drafts}`))).toBeVisible();
});

test("3. 지원을 만들고 매칭을 실행해 후보를 채택한다", async ({ page }) => {
  await page.goto(postingUrl);
  await page.getByRole("button", { name: "지원 만들기" }).click();
  await expect(page).toHaveURL(/\/applications\/[0-9a-f-]+$/);
  applicationUrl = page.url();

  await page.getByRole("link", { name: "공고 매칭" }).click();
  await expect(page).toHaveURL(/\/matches$/);
  await page.getByRole("button", { name: "매칭 실행" }).click();
  await waitForJobSummary(page, /요구사항 \d+개 · 후보 \d+건/);

  const accept = page.getByRole("button", { name: "채택", exact: true });
  await expect(accept.first()).toBeVisible();
  await accept.first().click();
  await expect(page.getByRole("button", { name: "되돌리기" }).first()).toBeVisible();
});

test("4–5. AI 초안을 만들고 근거 없는 문장을 승인해 버전을 저장하고 비교한다", async ({ page }) => {
  await page.goto(applicationUrl);
  await page.getByRole("button", { name: "새 문서" }).click();
  await page.getByRole("textbox", { name: /^제목/ }).fill("자기소개서 E2E");
  await page.getByRole("button", { name: "만들고 편집" }).click();
  await expect(page).toHaveURL(/\/documents\/[0-9a-f-]+$/);
  documentUrl = page.url();

  await page.getByRole("button", { name: "AI 초안 생성" }).click();
  await waitForJobSummary(page, /블록 \d+개 생성/);
  await expect(page.getByText(/승인 필요 \d+개/).first()).toBeVisible();

  const approvals = page.getByLabel("내보내기 승인");
  const pending = await approvals.count();
  for (let i = 0; i < pending; i++) await approvals.nth(i).check();
  await page.getByLabel("버전 이름").fill("E2E 검토본");
  await page.getByRole("button", { name: "버전 저장" }).click();
  await expect(page.getByText("모든 블록 내보내기 가능")).toBeVisible();
  await expect(page.getByText("E2E 검토본")).toBeVisible();

  await page.getByRole("link", { name: "비교", exact: true }).click();
  await expect(page).toHaveURL(/\/compare/);
  await expect(page.getByText(/변경 \d+/)).toBeVisible();
});

test("6. DOCX와 PDF를 내보내고 실제 파일을 받는다", async ({ page }) => {
  await page.goto(documentUrl);
  for (const format of ["Word (DOCX)", "PDF"]) {
    await page.getByLabel("내보내기 형식").selectOption({ label: format });
    await page.getByRole("button", { name: "파일 만들기" }).click();
    await waitForJobSummary(page, /파일이 준비되었습니다|이미 만든 파일/);
  }
  await expect(page.getByText("준비됨").first()).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "내려받기" }).first().click();
  const file = await download;
  expect(file.suggestedFilename()).toMatch(/\.(docx|pdf)$/);
  const path = await file.path();
  expect(path).toBeTruthy();
});

test("7. 제출 스냅샷은 이후 편집과 무관하게 그대로 열린다", async ({ page }) => {
  await page.goto(applicationUrl);
  await page.getByRole("button", { name: "제출 기록" }).click();
  await page.getByRole("combobox", { name: /제출한 문서 버전/ }).selectOption({ index: 1 });
  await page.getByRole("button", { name: "스냅샷 고정" }).click();
  await expect(page.getByText("제출 스냅샷", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("준비 → 제출")).toBeVisible();

  const snapshotLink = page.getByRole("link", { name: /자기소개서 E2E — / });
  submissionUrl = (await snapshotLink.getAttribute("href")) ?? "";
  expect(submissionUrl).toMatch(/\/submissions\//);

  // Edit the document afterwards; the snapshot must not follow.
  await page.goto(documentUrl);
  const first = page.getByRole("textbox", { name: /문장 [a-z]+-1/ }).first();
  const original = await first.inputValue();
  await first.fill(`${original} (제출 후 수정)`);
  await page.getByRole("button", { name: "버전 저장" }).click();
  await expect(page.getByText("새 버전을 저장했습니다.")).toBeVisible();

  await page.goto(submissionUrl);
  await expect(page.getByText("제출 스냅샷 · 변경 불가")).toBeVisible();
  await expect(page.getByText(original, { exact: true })).toBeVisible();
  await expect(page.getByText("(제출 후 수정)")).toHaveCount(0);
});

test("8. 서류 탈락을 기록하고 비교 대상을 인용해 회고를 남긴다", async ({ page }) => {
  await page.goto(applicationUrl);
  await page.getByRole("button", { name: "→ 서류 탈락" }).click();
  await expect(page.getByText("서류 탈락", { exact: true }).first()).toBeVisible();

  await expect(page.getByRole("heading", { name: "비교 대상" })).toBeVisible();
  await page.getByRole("button", { name: "근거로 인용" }).first().click();
  await expect(page.getByRole("textbox", { name: /^근거/ })).not.toHaveValue("");
  await page.getByRole("textbox", { name: /^관찰 사실/ }).fill("제출 8일 후 불합격 안내 메일 수신");
  await page.getByRole("textbox", { name: /^가설/ }).fill("필수 요구 경력 근거가 약했을 수 있음");
  await page.getByRole("button", { name: "회고 저장" }).click();
  await expect(page.getByText("회고 완료", { exact: true }).first()).toBeVisible();
});

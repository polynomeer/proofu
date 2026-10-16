import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

/**
 * F13 (docs/ai/ai-feature-spec.md): a pasted resume becomes career entry proposals, the user
 * compares each with the line it came from and keeps the ones they want. The fake provider
 * reads the dated lines, which is enough to exercise the whole path.
 */
const RESUME = `홍길동
gildong.hong@example.com | 010-1234-5678 | 서울

경력
2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어
결제 시스템을 재작성해 응답 시간을 줄였습니다.

2017.03-2020.02 다른회사 / 주니어 개발자
내부 관리 도구를 만들었습니다.

학력
2013.03 - 2017.02 프루프대학교 / 컴퓨터공학 학사`;

test("F13: 이력서를 붙여넣어 경력 후보를 보고 고른 것만 추가한다", async ({ page }) => {
  await switchUser(unique("import"), "가져오기 테스트");

  await page.goto("/career");
  await page.getByRole("link", { name: "이력서 가져오기" }).click();
  await expect(page).toHaveURL(/\/career\/import$/);

  await page.getByLabel("이력서 원문").fill(RESUME);
  await page.getByRole("button", { name: "경력 찾기" }).click();

  // Three dated lines, three proposals, each quoting the line it came from. The quote is
  // asserted inside the proposal, not on the page: the same words are still in the textarea.
  await expect(page.getByRole("heading", { name: "찾은 경력 3건" })).toBeVisible({
    timeout: 60_000,
  });
  const rows = page.getByRole("listitem").filter({ has: page.getByRole("checkbox") });
  await expect(rows).toHaveCount(3);
  const employment = rows.filter({ hasText: "백엔드 엔지니어" });
  await expect(employment.locator("blockquote")).toHaveText(
    "2020.03 - 2023.02 프루프 주식회사 / 백엔드 엔지니어",
  );
  await expect(employment).toContainText("근무 경력 · 프루프 주식회사 · 2020-03-01 – 2023-02-01");
  await expect(employment).toContainText("결제 시스템을 재작성해 응답 시간을 줄였습니다.");
  await expect(rows.filter({ hasText: "컴퓨터공학 학사" })).toContainText("학력 · 프루프대학교");
  // The contact details never left the server.
  await expect(page.getByText(/연락처 2곳을 지우고 분석했습니다/)).toBeVisible();

  // Keep two of the three: untick the education row.
  await rows.filter({ hasText: "컴퓨터공학 학사" }).getByRole("checkbox").uncheck();
  await page.getByRole("button", { name: "선택한 2건 추가" }).click();
  await expect(page.getByText("2건을 추가했습니다.")).toBeVisible();

  // What was added is in the career list; what was not stays a proposal and is never stored.
  await page.goto("/career");
  await expect(page.getByRole("link", { name: /백엔드 엔지니어/ })).toBeVisible();
  await expect(page.getByRole("link", { name: /주니어 개발자/ })).toBeVisible();
  await expect(page.getByText("컴퓨터공학 학사")).toHaveCount(0);
});

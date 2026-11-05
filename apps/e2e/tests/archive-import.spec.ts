import { readFileSync } from "node:fs";
import { join } from "node:path";

import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

/**
 * F21 1단계 (docs/product/personal-archive-import.md): a bundle out of someone's own job-search
 * archive is planned first, every verdict is shown, and only the picked items become rows.
 *
 * The fixture is the same synthetic bundle an adapter would produce, so this exercises what the
 * screen actually has to survive: a rejected item, a picked item whose posting was left behind,
 * and a posting that becomes an imported snapshot with the archive's own text.
 */
const BUNDLE = readFileSync(
  join(__dirname, "../../../fixtures/imports/career-hub-sample.json"),
  "utf8",
);

test("F21: 번들을 먼저 계획하고 고른 것만 들여온다", async ({ page }) => {
  await switchUser(unique("archive"), "자료 가져오기 테스트");

  await page.goto("/career");
  await page.getByRole("link", { name: "자료 가져오기" }).click();
  await expect(page).toHaveURL(/\/career\/imports$/);

  await page.getByLabel("또는 붙여넣기").fill(BUNDLE);
  await expect(page.getByText("6건을 읽었습니다")).toBeVisible();
  await page.getByRole("button", { name: "무엇이 들어올지 보기" }).click();

  // Four can be created; the document and the application with no posting cannot.
  await expect(page.getByRole("heading", { name: "계획" })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText("새 항목 4")).toBeVisible();
  await expect(page.getByText("들일 수 없음 2")).toBeVisible();
  const rows = page.getByRole("listitem").filter({ has: page.getByRole("checkbox") });
  await expect(rows).toHaveCount(6);
  await expect(rows.filter({ hasText: "orphan-role" })).toContainText("공고가 없습니다");
  await expect(rows.filter({ hasText: "orphan-role" }).getByRole("checkbox")).toBeDisabled();
  await expect(rows.filter({ hasText: "resumes/2026-03-nimbus.md" })).toContainText(
    "document 항목을 아직 들이지 않습니다",
  );

  // Leave one posting behind: its application must wait rather than get a made-up posting.
  await rows.filter({ hasText: "jd-analysis/mirae-platform.md" }).getByRole("checkbox").uncheck();
  await page.getByRole("button", { name: "고른 3건 들이기" }).click();

  await expect(page.getByRole("heading", { name: "결과" })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText("2건이 들어왔습니다.")).toBeVisible();
  await expect(page.getByText(/가리키는 공고가 아직 들어오지 않았습니다/)).toBeVisible();

  // The posting carries the archive's own text, marked as imported.
  await page.goto("/jobs");
  await page.getByRole("link", { name: /님버스테크/ }).click();
  await expect(page.getByText("가져온 자료")).toBeVisible();
  await expect(page.getByText("서버 개발 경력 5년 이상")).toBeVisible();

  // The application is at the status the archive recorded, and the one that waited is absent.
  await page.goto("/applications");
  await expect(page.getByText("님버스테크").first()).toBeVisible();
  await expect(page.getByText("미래플랫폼")).toHaveCount(0);

  // A second plan of the same bundle has nothing new to offer.
  await page.goto("/career/imports");
  await expect(page.getByText("들여옴")).toBeVisible();
  await page.getByLabel("또는 붙여넣기").fill(BUNDLE);
  await page.getByRole("button", { name: "무엇이 들어올지 보기" }).click();
  await expect(page.getByRole("heading", { name: "계획" })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText("이미 들여옴 2")).toBeVisible();
  await expect(page.getByText("새 항목 2")).toBeVisible();
});

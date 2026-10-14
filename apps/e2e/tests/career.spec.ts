import { expect, test, type Page } from "@playwright/test";

import { API, apiAs, switchUser, unique } from "./helpers";

/**
 * US01 (docs/product/prd.md): career records are created, edited and deleted in the UI. Each
 * saved edit raises the revision shown on the detail page, and an edit made from a stale page
 * is refused instead of overwriting the newer one.
 */
function revision(page: Page) {
  return page.locator("dt", { hasText: /^revision$/ }).locator("+ dd");
}

test("US01: 경력·프로젝트·성과를 화면에서 만들고 고치고 지운다", async ({ page }) => {
  const sub = unique("career");
  await switchUser(sub, "커리어 테스트");
  // Deletes ask for confirmation in a native dialog.
  page.on("dialog", (d) => d.accept());

  // Create a career entry through the form.
  await page.goto("/career/new");
  await page.getByLabel(/^유형/).selectOption("EMPLOYMENT");
  await page.getByRole("textbox", { name: /^제목/ }).fill("데이터 엔지니어");
  await page.getByRole("textbox", { name: /^회사 · 조직/ }).fill("예시 데이터");
  await page.getByLabel(/^시작일/).fill("2021-03-01");
  await page.getByRole("button", { name: "경력 저장" }).click();
  await expect(page).toHaveURL(/\/career\/[0-9a-f-]+$/);
  const entryUrl = new URL(page.url()).pathname;
  const entryId = entryUrl.split("/").pop()!;
  await expect(page.getByRole("heading", { level: 1, name: "데이터 엔지니어" })).toBeVisible();
  await expect(revision(page)).toHaveText("1");

  // Edit it: the change shows and the revision goes up.
  await page.goto(`${entryUrl}/edit`);
  await page.getByRole("textbox", { name: /^제목/ }).fill("시니어 데이터 엔지니어");
  await page.getByRole("button", { name: "변경 사항 저장" }).click();
  await expect(page).toHaveURL(new RegExp(`${entryUrl}$`));
  await expect(
    page.getByRole("heading", { level: 1, name: "시니어 데이터 엔지니어" }),
  ).toBeVisible();
  await expect(revision(page)).toHaveText("2");

  // An edit started before someone else saved is refused, not silently overwritten.
  await page.goto(`${entryUrl}/edit`);
  const api = await apiAs(sub);
  const elsewhere = await api.patch(`${API}/career-entries/${entryId}`, {
    data: {
      type: "EMPLOYMENT",
      title: "리드 데이터 엔지니어",
      organization: "예시 데이터",
      startDate: "2021-03-01",
      revision: 2,
    },
  });
  expect(elsewhere.status(), await elsewhere.text()).toBe(200);
  await api.dispose();
  await page.getByRole("textbox", { name: /^제목/ }).fill("내 화면의 제목");
  await page.getByRole("button", { name: "변경 사항 저장" }).click();
  await expect(page.getByText(/다른 곳에서 먼저 수정되었습니다/)).toBeVisible();
  await page.goto(entryUrl);
  await expect(page.getByRole("heading", { level: 1, name: "리드 데이터 엔지니어" })).toBeVisible();
  await expect(revision(page)).toHaveText("3");

  // A project under the entry, then an achievement under the project.
  await page.getByRole("link", { name: "프로젝트 추가" }).click();
  await page.getByRole("textbox", { name: /^프로젝트명/ }).fill("스트리밍 수집 파이프라인");
  await page.getByRole("textbox", { name: /^역할/ }).fill("데이터 엔지니어");
  await page.getByRole("textbox", { name: /^요약/ }).fill("배치 수집을 스트리밍으로 바꿨습니다.");
  await page.getByRole("button", { name: "프로젝트 저장" }).click();
  await expect(page).toHaveURL(/\/projects\/[0-9a-f-]+$/);
  const projectUrl = new URL(page.url()).pathname;
  await expect(
    page.getByRole("heading", { level: 1, name: "스트리밍 수집 파이프라인" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "성과 추가" }).click();
  await page.getByRole("textbox", { name: /^행동/ }).fill("수집 경로를 Kafka 스트리밍으로 전환");
  await page.getByRole("textbox", { name: /^결과/ }).fill("적재 지연을 하루에서 5분으로 단축");
  await page.getByRole("button", { name: "성과 추가" }).click();
  await expect(page.getByText("적재 지연을 하루에서 5분으로 단축")).toBeVisible();

  const achievement = (text: string) =>
    page
      .getByRole("listitem")
      .filter({ hasText: text })
      .filter({ has: page.getByRole("button", { name: "편집" }) });
  await achievement("5분으로 단축").getByRole("button", { name: "편집", exact: true }).click();
  await page.getByRole("textbox", { name: /^결과/ }).fill("적재 지연을 하루에서 3분으로 단축");
  await page.getByRole("button", { name: "변경 사항 저장" }).click();
  await expect(page.getByText("적재 지연을 하루에서 3분으로 단축")).toBeVisible();
  await expect(page.getByText("적재 지연을 하루에서 5분으로 단축")).toHaveCount(0);

  // Deleting goes child first: the achievement, then the project, then the entry.
  await achievement("3분으로 단축").getByRole("button", { name: "삭제", exact: true }).click();
  await expect(page.getByText("적재 지연을 하루에서 3분으로 단축")).toHaveCount(0);

  await page.goto(projectUrl);
  await page.getByRole("button", { name: "삭제", exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`${entryUrl}$`));
  await expect(page.getByText("스트리밍 수집 파이프라인")).toHaveCount(0);

  await page.getByRole("button", { name: "삭제", exact: true }).first().click();
  await expect(page).toHaveURL(/\/career$/);
  await expect(page.getByText("리드 데이터 엔지니어")).toHaveCount(0);
  await expect(page.getByText("아직 등록된 경력이 없습니다")).toBeVisible();
  await page.goto(entryUrl);
  await expect(page.getByText("경력을 찾을 수 없습니다")).toBeVisible();
});

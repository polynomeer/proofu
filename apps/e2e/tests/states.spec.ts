import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

/** 공통 상태 (docs/ux/screen-specifications.md): 없는 주소와 없는 리소스가 어떻게 보이는지. */
test("상태: 없는 주소와 없는 리소스는 각각의 안내를 보여 준다", async ({ page }) => {
  await switchUser(unique("states"), "상태 테스트");

  const unknown = await page.goto("/이런-주소는-없습니다");
  expect(unknown!.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "페이지를 찾을 수 없습니다" })).toBeVisible();
  await page.getByRole("link", { name: "대시보드로" }).click();
  await expect(page).toHaveURL(/\/$/);

  // A well-formed id that belongs to nobody: the resource's own wording, inside the app shell.
  // Next answers 200 for a notFound() raised inside a streamed dynamic route; the app is behind
  // login, so the wording is what matters here, not the status.
  await page.goto("/projects/01a0c000-0000-7000-8000-000000000000");
  await expect(page.getByRole("heading", { name: "프로젝트를 찾을 수 없습니다" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "주 메뉴" })).toBeVisible();
});

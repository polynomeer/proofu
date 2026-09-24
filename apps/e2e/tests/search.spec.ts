import { expect, test } from "@playwright/test";

import { apiAs, created, switchUser, unique } from "./helpers";

test("검색: 상단 검색창이 커리어·기술·Evidence를 한 번에 찾는다", async ({ page }) => {
  const sub = unique("search");
  await switchUser(sub, "검색 테스트");
  const api = await apiAs(sub);
  const entry = await created(api, "/career-entries", {
    type: "EMPLOYMENT",
    title: "결제 플랫폼 엔지니어",
    organization: "예시 주식회사",
    startDate: "2021-01-01",
  });
  await created(api, "/projects", {
    careerEntryId: entry,
    name: "결제 재구축",
    role: "리드",
    summary: "파이프라인을 재설계했습니다.",
  });
  await created(api, "/skills", {
    canonicalName: "Kotlin",
    category: "PROGRAMMING_LANGUAGE",
    aliases: ["결제 코틀린"],
  });
  await api.dispose();

  await page.goto("/career");
  await page.getByRole("searchbox", { name: "통합 검색" }).fill("결제");
  await page.getByRole("searchbox", { name: "통합 검색" }).press("Enter");
  await expect(page).toHaveURL(/\/search\?q=/);

  await expect(page.getByRole("heading", { name: /"결제" 검색 결과/ })).toBeVisible();
  await expect(page.getByRole("link", { name: /결제 플랫폼 엔지니어/ })).toBeVisible();
  await expect(page.getByRole("link", { name: /결제 재구축/ })).toBeVisible();
  // Matched through its alias, not its name.
  await expect(page.getByRole("link", { name: /Kotlin/ })).toBeVisible();

  await page.getByRole("link", { name: /결제 플랫폼 엔지니어/ }).click();
  await expect(page).toHaveURL(new RegExp(`/career/${entry}$`));

  // Nothing matching says so instead of showing an empty page.
  await page.goto("/search?q=존재하지않는낱말");
  await expect(page.getByRole("heading", { name: "일치하는 기록이 없습니다" })).toBeVisible();
});

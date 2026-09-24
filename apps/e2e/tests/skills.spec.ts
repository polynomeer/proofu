import { expect, test } from "@playwright/test";

import { apiAs, created, switchUser, unique } from "./helpers";

test("커리어: 기술을 등록해 중복을 막고 프로젝트에 연결한다", async ({ page }) => {
  const sub = unique("skills");
  await switchUser(sub, "기술 테스트");
  const api = await apiAs(sub);
  const entry = await created(api, "/career-entries", {
    type: "EMPLOYMENT",
    title: "백엔드 엔지니어",
    startDate: "2022-01-01",
  });
  const project = await created(api, "/projects", {
    careerEntryId: entry,
    name: "결제 재구축",
    role: "리드",
    summary: "결제 파이프라인을 재구축했습니다.",
  });
  await api.dispose();

  await page.goto("/career");
  await page.getByRole("link", { name: "기술 관리" }).click();
  await expect(page).toHaveURL(/\/career\/skills$/);

  await page.getByRole("button", { name: "기술 추가" }).click();
  await page.getByRole("textbox", { name: /^이름/ }).fill("Spring Boot");
  await page.getByLabel("분류").selectOption("FRAMEWORK");
  await page.getByRole("textbox", { name: /^다른 이름/ }).fill("SpringBoot, 스프링 부트");
  await page.getByLabel(/숙련도/).selectOption("ADVANCED");
  await page.getByRole("button", { name: "기술 추가", exact: true }).last().click();
  await expect(page.getByText("Spring Boot", { exact: true })).toBeVisible();
  await expect(page.getByText("고급")).toBeVisible();

  // The same skill under another spelling is refused, not silently duplicated.
  await page.getByRole("button", { name: "기술 추가" }).first().click();
  await page.getByRole("textbox", { name: /^이름/ }).fill("springboot");
  await page.getByRole("button", { name: "기술 추가", exact: true }).last().click();
  await expect(page.getByText(/이미 같은 기술이 있습니다/)).toBeVisible();
  await page.getByRole("button", { name: "취소" }).click();

  await page.goto(`/projects/${project}`);
  await page.getByRole("button", { name: "연결" }).click();
  await page.getByRole("checkbox", { name: "Spring Boot" }).check();
  await page.getByRole("button", { name: "저장" }).click();
  await expect(page.getByText("Spring Boot · 고급")).toBeVisible();

  // The link survives a reload and follows the skill, not a copy of its name.
  await page.reload();
  await expect(page.getByText("Spring Boot · 고급")).toBeVisible();
});

test("커리어: 기술이 한 페이지를 넘으면 더 보기로 이어서 보여 준다", async ({ page }) => {
  const sub = unique("skills-page");
  await switchUser(sub, "기술 페이지 테스트");
  const api = await apiAs(sub);
  // One past the page size, so the list must offer the rest instead of stopping.
  for (let i = 0; i < 51; i++) {
    await created(api, "/skills", { canonicalName: `스킬 ${i}`, category: "TOOL" });
  }
  await api.dispose();

  await page.goto("/career/skills");
  const rows = page.getByRole("listitem").filter({ hasText: /^스킬 / });
  await expect(rows).toHaveCount(50);
  await page.getByRole("button", { name: "더 보기" }).click();
  await expect(rows).toHaveCount(51);
  await expect(page.getByRole("button", { name: "더 보기" })).toBeHidden();
});

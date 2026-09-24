import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

test.use({ viewport: { width: 390, height: 844 } });

/** Phone width: four tabs plus 더보기, which must actually lead somewhere. */
test("모바일: 더보기에서 나머지 메뉴와 로그아웃에 닿는다", async ({ page }) => {
  await switchUser(unique("mobile"), "모바일 테스트");
  await page.goto("/career");

  const bottom = page.getByRole("navigation", { name: "주 메뉴" });
  await expect(bottom.getByRole("link", { name: "커리어" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await bottom.getByRole("link", { name: "더보기" }).click();
  await expect(page).toHaveURL(/\/more$/);
  await expect(bottom.getByRole("link", { name: "더보기" })).toHaveAttribute(
    "aria-current",
    "page",
  );

  // The destinations the bottom bar has no room for.
  for (const label of ["지원 문서", "지원 관리", "설정", "도움말"]) {
    await expect(page.getByRole("link", { name: label })).toBeVisible();
  }
  await page.getByRole("link", { name: "설정" }).click();
  await expect(page).toHaveURL(/\/settings$/);

  // Sign-out lives here on a phone; the desktop top bar has no room either.
  await page.goto("/more");
  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL(/\/auth\/signed-out$/);

  // Nothing on a phone scrolls sideways.
  await page.goto("/career");
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  );
  expect(overflow).toBe(false);
});

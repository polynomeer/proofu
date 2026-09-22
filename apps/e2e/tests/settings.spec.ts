import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

test("설정: AI 동의는 재인증을 거쳐 저장되고 공개 범위 기본값과 보존 기간이 적용된다", async ({
  page,
}) => {
  const sub = unique("settings");
  await switchUser(sub, "설정 테스트");
  await page.goto("/settings");

  await page.getByLabel("기밀 민감도 기록을 AI 처리에 포함하는 데 동의합니다.").check();
  await page.getByLabel(/새 기록의 공개 범위 기본값/).selectOption("SELECTIVE");
  await page.getByRole("spinbutton", { name: /휴지통 보존/ }).fill("14");
  await page.getByRole("spinbutton", { name: /내보내기 파일 보존/ }).fill("3");
  await page.getByRole("button", { name: "설정 저장" }).click();
  // Fresh mock login → auth_time is recent → saved without a detour.
  await expect(page.getByText("저장했습니다.")).toBeVisible();
  await expect(page.getByText(/동의 \d{4}/)).toBeVisible();

  await page.reload();
  await expect(page.getByRole("spinbutton", { name: /휴지통 보존/ })).toHaveValue("14");
  await expect(page.getByRole("spinbutton", { name: /내보내기 파일 보존/ })).toHaveValue("3");

  await page.goto("/career/new");
  await expect(page.getByLabel(/공개 범위/)).toHaveValue("SELECTIVE");
});

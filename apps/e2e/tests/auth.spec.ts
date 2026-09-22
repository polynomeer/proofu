import { expect, test } from "@playwright/test";

import { API, switchUser, unique } from "./helpers";

test.describe("인증 (ADR-0010)", () => {
  test("로그인 없이 앱 페이지에 가면 IdP를 거쳐 돌아오고, 로그아웃하면 세션이 사라진다", async ({
    page,
  }) => {
    const sub = unique("auth");
    await switchUser(sub, "인증 테스트");
    await page.goto("/settings");
    await expect(page).toHaveURL(/\/settings$/);
    await expect(page.getByRole("button", { name: "로그아웃" })).toBeVisible();
    await expect(page.getByText("인증 테스트")).toBeVisible();

    await page.getByRole("button", { name: "로그아웃" }).click();
    await expect(page).toHaveURL(/\/auth\/signed-out$/);
    const cookies = await page.context().cookies();
    expect(cookies.map((c) => c.name)).not.toContain("proofu_session");
  });

  test("API는 헤더 신원을 무시하고 토큰만 받는다", async ({ request }) => {
    const anonymous = await request.get(`${API}/career-entries`, {
      headers: { "X-Workspace-Id": "00000000-0000-7000-8000-000000000002" },
    });
    expect(anonymous.status()).toBe(401);
    expect(((await anonymous.json()) as { code: string }).code).toBe("UNAUTHENTICATED");
  });
});

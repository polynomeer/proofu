import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

// Apple's fetcher and a phone without the app both arrive with no session (ADR-0011 §3).
test.use({ storageState: { cookies: [], origins: [] } });

test("iOS 앱 연결: 연결 파일은 세션 없이 JSON으로 열리고 OIDC 콜백만 앱에 넘긴다", async ({
  request,
}) => {
  const response = await request.get("/.well-known/apple-app-site-association", {
    maxRedirects: 0,
  });
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("application/json");
  const body = await response.json();
  expect(body.applinks.details).toEqual([
    {
      appIDs: ["ABCDE12345.dev.proofu.e2e"],
      components: [{ "/": "/auth/ios/callback", comment: "OIDC redirect for the iOS app" }],
    },
  ]);
});

test("iOS 앱 연결: 앱이 없는 기기의 콜백은 로그인으로 튕기지 않고 안내만 한다", async ({
  page,
}) => {
  await page.goto("/auth/ios/callback?code=not-for-the-web&state=x");
  await expect(page).toHaveURL(/\/auth\/ios\/callback/);
  await expect(
    page.getByRole("heading", { name: "ProofU 앱에서 로그인을 마쳐 주세요" }),
  ).toBeVisible();
  // Outside the app shell and too small for the shared scan's rule-count floor; checked here.
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();
  expect(violations.map((v) => v.id)).toEqual([]);
});

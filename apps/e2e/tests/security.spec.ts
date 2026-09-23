import { expect, test } from "@playwright/test";

import { switchUser, unique } from "./helpers";

test("보안 헤더: 페이지는 nonce CSP와 기본 헤더를 보내고 위반 없이 렌더된다", async ({ page }) => {
  await switchUser(unique("security"), "보안 테스트");

  const violations: string[] = [];
  page.on("console", (message) => {
    if (/content security policy/i.test(message.text())) violations.push(message.text());
  });

  const response = await page.goto("/career");
  const headers = response!.headers();

  const csp = headers["content-security-policy"]!;
  expect(csp).toMatch(/script-src [^;]*'nonce-[^']+'/);
  expect(csp).toContain("'strict-dynamic'");
  expect(csp).not.toContain("'unsafe-inline' 'nonce"); // inline script stays refused
  expect(csp).toContain("frame-ancestors 'none'");
  expect(csp).toContain("object-src 'none'");
  expect(csp).toContain("upgrade-insecure-requests"); // production build
  // Logout posts to /auth/logout and is redirected on to the IdP; Chrome checks the whole chain.
  expect(csp).toMatch(/form-action 'self' http:\/\/localhost:8181/);
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["x-frame-options"]).toBe("DENY");
  expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
  expect(headers["permissions-policy"]).toContain("camera=()");
  expect(headers["strict-transport-security"]).toContain("max-age=31536000");

  // Next stamps the nonce on its own scripts; nothing renders without them.
  await expect(page.getByRole("heading", { name: "커리어" })).toBeVisible();
  const scripts = await page.locator("script[nonce]").count();
  expect(scripts).toBeGreaterThan(0);
  expect(violations).toEqual([]);

  // Each response carries a fresh nonce.
  const second = await page.goto("/evidence");
  expect(second!.headers()["content-security-policy"]).not.toBe(csp);

  // The BFF proxy answers with the same baseline headers.
  const api = await page.request.get("/api/v1/skills");
  expect(api.headers()["x-content-type-options"]).toBe("nosniff");
});

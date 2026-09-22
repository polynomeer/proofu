import { expect, test } from "@playwright/test";

import { API, apiAs, created, switchUser, unique } from "./helpers";

test("10. 계정을 삭제하면 즉시 접근이 막히고 삭제 작업이 끝난다", async ({ page }) => {
  const sub = unique("delete");
  await switchUser(sub, "삭제 테스트");
  const api = await apiAs(sub);
  await created(api, "/career-entries", {
    type: "EMPLOYMENT",
    title: "지울 경력",
    startDate: "2020-01-01",
  });

  await page.goto("/settings");
  await page.getByRole("button", { name: "계정 삭제…" }).click();
  await page.getByLabel(/확인을 위해/).fill("삭제");
  await page.getByRole("button", { name: "영구 삭제" }).click();
  await expect(page).toHaveURL(/\/auth\/signed-out$/);

  // Marked deleted: the API refuses the same identity at once.
  await expect.poll(async () => (await api.get(`${API}/career-entries`)).status()).toBe(401);
  await api.dispose();

  // Once purged, the scrubbed identity is gone and the subject registers as a brand-new user.
  await expect
    .poll(
      async () => {
        const fresh = await apiAs(sub);
        const r = await fresh.get(`${API}/career-entries`);
        const body = r.ok() ? ((await r.json()) as { items: unknown[] }) : null;
        await fresh.dispose();
        return body ? body.items.length : -1;
      },
      { timeout: 30_000 },
    )
    .toBe(0);
});

import { expect, test } from "@playwright/test";

import { API, apiAs, created, switchUser, unique } from "./helpers";

test("Evidence: 역량을 정의하고 Evidence를 연결하면 근거 상태가 바뀐다", async ({ page }) => {
  const sub = unique("capability");
  await switchUser(sub, "역량 테스트");
  const api = await apiAs(sub);
  await created(api, "/evidence", {
    type: "URL",
    title: "장애 회고 문서",
    source: "USER_INPUT",
    uri: "https://example.test/postmortem",
    verification: "USER_VERIFIED",
    capturedAt: "2026-02-01T00:00:00Z",
  });

  await page.goto("/evidence");
  await page.getByRole("link", { name: "역량 관리" }).click();
  await expect(page).toHaveURL(/\/evidence\/capabilities$/);

  await page.getByRole("button", { name: "역량 추가" }).click();
  await page.getByRole("textbox", { name: /^역량 이름/ }).fill("분산 시스템 설계");
  await page.getByLabel("분류").selectOption("SYSTEM_DESIGN");
  await page.getByRole("textbox", { name: /^정의/ }).fill("장애를 견디는 시스템을 설계한다");
  await page.getByLabel(/수준/).selectOption("INDEPENDENT");
  await page.getByRole("button", { name: "역량 추가", exact: true }).last().click();
  await expect(page.getByText("근거 없음")).toBeVisible();
  await expect(page.getByText("자기평가 독립 수행")).toBeVisible();

  await page.getByRole("button", { name: "Evidence 연결" }).click();
  await page.getByRole("checkbox", { name: "장애 회고 문서" }).check();
  await page.getByRole("button", { name: "저장" }).click();
  await expect(page.getByText("검증된 근거")).toBeVisible();

  // A capability may not be nested under its own descendant.
  const list = (await (await api.get(`${API}/capabilities`)).json()) as {
    items: { id: string; name: string; definition: string; category: string; revision: number }[];
  };
  const root = list.items[0]!;
  const child = await api.post(`${API}/capabilities`, {
    data: {
      name: "합의 알고리즘",
      definition: "합의 알고리즘을 고르고 운영한다",
      category: "SYSTEM_DESIGN",
      parentId: root.id,
    },
  });
  const childId = ((await child.json()) as { id: string }).id;
  const cycle = await api.patch(`${API}/capabilities/${root.id}`, {
    data: {
      name: root.name,
      definition: root.definition,
      category: root.category,
      parentId: childId,
      revision: root.revision,
    },
  });
  expect(cycle.status()).toBe(422);
  await api.dispose();

  // The child shows under its parent, and deleting the parent takes it along.
  await page.reload();
  await expect(page.getByText(/합의 알고리즘 · 근거 없음/)).toBeVisible();
  page.once("dialog", (d) => d.accept());
  await page.getByRole("button", { name: "삭제" }).first().click();
  await expect(page.getByText("아직 정의한 역량이 없습니다")).toBeVisible();
});

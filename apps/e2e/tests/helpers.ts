import { expect, request, type APIRequestContext, type Page } from "@playwright/test";

export const IDP = process.env.E2E_IDP_URL ?? "http://localhost:8181/realms/mock";
export const API = process.env.E2E_API_URL ?? "http://localhost:8080/api/v1";

/** Makes `sub` the user the mock IdP signs in on the next login. */
export async function switchUser(sub: string, name = sub) {
  const r = await fetch(`${IDP}/dev/user`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ sub, email: `${sub}@example.test`, name }),
  });
  expect(r.ok).toBeTruthy();
}

/** An API client authenticated as `sub` (token minted by the mock IdP, verified by the API's JWKS). */
export async function apiAs(sub: string, ageSeconds = 0): Promise<APIRequestContext> {
  const r = await fetch(`${IDP}/dev/token?sub=${encodeURIComponent(sub)}&age=${ageSeconds}`);
  const { access_token } = (await r.json()) as { access_token: string };
  return request.newContext({
    extraHTTPHeaders: {
      Authorization: `Bearer ${access_token}`,
      "Content-Type": "application/json",
    },
  });
}

export async function created(
  api: APIRequestContext,
  path: string,
  body: unknown,
): Promise<string> {
  const r = await api.post(`${API}${path}`, { data: body });
  expect(r.status(), `${path}: ${await r.text()}`).toBe(201);
  return ((await r.json()) as { id: string }).id;
}

/** Logs in through the real redirect chain (web → mock IdP → callback) and lands on `path`. */
export async function login(page: Page, path = "/") {
  await page.goto(path);
  await expect(page).toHaveURL(new RegExp(`${path.replace("/", "\\/")}$`));
}

export function unique(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
}

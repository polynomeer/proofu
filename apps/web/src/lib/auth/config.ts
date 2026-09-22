import "server-only";

/**
 * ADR-0010 결정 1: the web app is the confidential OIDC client (BFF). `header` mode is the
 * interim local setup that sends X-Workspace-Id; `oidc` mode holds the user's tokens in an
 * encrypted httpOnly cookie and the browser never sees them.
 */
export type AuthMode = "header" | "oidc";

export const AUTH_MODE: AuthMode = process.env.AUTH_MODE === "oidc" ? "oidc" : "header";
export const API_ORIGIN = process.env.API_ORIGIN ?? "http://localhost:8080";
export const APP_ORIGIN = process.env.APP_ORIGIN ?? "http://localhost:3000";
export const WORKSPACE_ID =
  process.env.NEXT_PUBLIC_WORKSPACE_ID ?? "00000000-0000-7000-8000-000000000002";

export type OidcConfig = {
  issuer: string;
  clientId: string;
  clientSecret: string;
  scopes: string;
};

export function oidcConfig(): OidcConfig {
  const issuer = process.env.OIDC_ISSUER;
  const clientId = process.env.OIDC_CLIENT_ID;
  const clientSecret = process.env.OIDC_CLIENT_SECRET;
  if (!issuer || !clientId || !clientSecret) {
    throw new Error("AUTH_MODE=oidc needs OIDC_ISSUER, OIDC_CLIENT_ID and OIDC_CLIENT_SECRET");
  }
  return {
    issuer,
    clientId,
    clientSecret,
    scopes: process.env.OIDC_SCOPES ?? "openid profile email",
  };
}

type Discovery = {
  authorization_endpoint: string;
  token_endpoint: string;
  end_session_endpoint?: string;
  issuer: string;
};

let discovery: Promise<Discovery> | null = null;

/** Discovery is fetched once per process; a failure is not cached so the next login retries. */
export function discover(): Promise<Discovery> {
  if (!discovery) {
    const { issuer } = oidcConfig();
    discovery = fetch(`${issuer.replace(/\/$/, "")}/.well-known/openid-configuration`, {
      cache: "no-store",
    })
      .then(async (r) => {
        if (!r.ok) throw new Error(`OIDC discovery failed: ${r.status}`);
        return (await r.json()) as Discovery;
      })
      .catch((e) => {
        discovery = null;
        throw e;
      });
  }
  return discovery;
}

import "server-only";

import { createHash, randomBytes } from "node:crypto";

import { discover, oidcConfig } from "@/lib/auth/config";

export type TokenResponse = {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
};

export type IdClaims = {
  iss: string;
  sub: string;
  aud: string | string[];
  nonce?: string;
  exp: number;
  auth_time?: number;
  email?: string;
  email_verified?: boolean;
  name?: string;
  preferred_username?: string;
};

export function pkcePair(): { verifier: string; challenge: string } {
  const verifier = randomBytes(32).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");
  return { verifier, challenge };
}

export function randomToken(): string {
  return randomBytes(16).toString("base64url");
}

async function tokenRequest(params: Record<string, string>): Promise<TokenResponse> {
  const { clientId, clientSecret } = oidcConfig();
  const { token_endpoint } = await discover();
  const body = new URLSearchParams({ ...params, client_id: clientId, client_secret: clientSecret });
  const r = await fetch(token_endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
    cache: "no-store",
  });
  if (!r.ok) throw new Error(`token endpoint answered ${r.status}`);
  return (await r.json()) as TokenResponse;
}

export function exchangeCode(code: string, verifier: string, redirectUri: string) {
  return tokenRequest({
    grant_type: "authorization_code",
    code,
    code_verifier: verifier,
    redirect_uri: redirectUri,
  });
}

export function refreshTokens(refreshToken: string) {
  return tokenRequest({ grant_type: "refresh_token", refresh_token: refreshToken });
}

/**
 * The ID token arrived over TLS straight from the token endpoint, so the code flow allows
 * skipping signature verification; issuer, audience, nonce and expiry are still checked.
 */
export function decodeIdToken(idToken: string, expected: { nonce: string }): IdClaims {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("malformed id_token");
  const claims = JSON.parse(Buffer.from(parts[1] ?? "", "base64url").toString("utf8")) as IdClaims;
  const { issuer, clientId } = oidcConfig();
  const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (claims.iss !== issuer) throw new Error("id_token issuer mismatch");
  if (!aud.includes(clientId)) throw new Error("id_token audience mismatch");
  if (claims.nonce !== expected.nonce) throw new Error("id_token nonce mismatch");
  if (claims.exp * 1000 < Date.now()) throw new Error("id_token expired");
  return claims;
}

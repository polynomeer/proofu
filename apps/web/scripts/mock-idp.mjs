#!/usr/bin/env node
// A minimal OIDC provider for local development and browser tests (ADR-0010 is provider-neutral):
// discovery, JWKS, authorize (no login form — the fixed user is signed in immediately), token
// (code + PKCE, refresh) and end-session. Never use outside localhost.
//
//   node apps/web/scripts/mock-idp.mjs            # http://localhost:8181/realms/mock
//   MOCK_IDP_PORT=9000 MOCK_IDP_USER_EMAIL=me@example.com node apps/web/scripts/mock-idp.mjs
import { createHash, createSign, generateKeyPairSync, randomBytes } from "node:crypto";
import { createServer } from "node:http";

const port = Number(process.env.MOCK_IDP_PORT ?? 8181);
const issuer = process.env.MOCK_IDP_ISSUER ?? `http://localhost:${port}/realms/mock`;
const base = new URL(issuer).pathname;
const user = {
  sub: process.env.MOCK_IDP_USER_SUB ?? "mock-user-0001",
  email: process.env.MOCK_IDP_USER_EMAIL ?? "dev@proofu.local",
  name: process.env.MOCK_IDP_USER_NAME ?? "Mock User",
};
const apiAudience = process.env.MOCK_IDP_API_AUDIENCE ?? "proofu-api";
const accessTtl = Number(process.env.MOCK_IDP_ACCESS_TTL ?? 900);

const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const kid = "mock-key-1";
const jwks = { keys: [{ ...publicKey.export({ format: "jwk" }), kid, use: "sig", alg: "RS256" }] };

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
function jwt(claims) {
  const input = `${b64({ alg: "RS256", typ: "JWT", kid })}.${b64(claims)}`;
  const signature = createSign("RSA-SHA256").update(input).sign(privateKey).toString("base64url");
  return `${input}.${signature}`;
}
const now = () => Math.floor(Date.now() / 1000);

const codes = new Map(); // code -> { clientId, redirectUri, nonce, challenge, authTime }
const refreshTokens = new Map(); // token -> { clientId, authTime }

function tokens({ clientId, nonce, authTime }) {
  const iat = now();
  const access = jwt({
    iss: issuer,
    sub: user.sub,
    aud: apiAudience,
    azp: clientId,
    iat,
    exp: iat + accessTtl,
    auth_time: authTime,
    email: user.email,
    email_verified: true,
    name: user.name,
    scope: "openid profile email",
  });
  const id = jwt({
    iss: issuer,
    sub: user.sub,
    aud: clientId,
    iat,
    exp: iat + accessTtl,
    auth_time: authTime,
    nonce,
    email: user.email,
    email_verified: true,
    name: user.name,
    preferred_username: user.email,
  });
  const refresh = randomBytes(24).toString("base64url");
  refreshTokens.set(refresh, { clientId, authTime });
  return {
    access_token: access,
    id_token: id,
    refresh_token: refresh,
    token_type: "Bearer",
    expires_in: accessTtl,
  };
}

const json = (res, status, body) => {
  res.writeHead(status, { "content-type": "application/json", "cache-control": "no-store" });
  res.end(JSON.stringify(body));
};
const redirect = (res, url) => {
  res.writeHead(302, { location: url });
  res.end();
};
const readForm = (req) =>
  new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => resolve(new URLSearchParams(data)));
  });

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const path = url.pathname.replace(base, "");
  if (path === "/.well-known/openid-configuration") {
    return json(res, 200, {
      issuer,
      authorization_endpoint: `${issuer}/protocol/openid-connect/auth`,
      token_endpoint: `${issuer}/protocol/openid-connect/token`,
      jwks_uri: `${issuer}/protocol/openid-connect/certs`,
      end_session_endpoint: `${issuer}/protocol/openid-connect/logout`,
      response_types_supported: ["code"],
      code_challenge_methods_supported: ["S256"],
      subject_types_supported: ["public"],
      id_token_signing_alg_values_supported: ["RS256"],
      scopes_supported: ["openid", "profile", "email"],
      claims_supported: ["sub", "email", "email_verified", "name", "auth_time"],
    });
  }
  if (path === "/protocol/openid-connect/certs") return json(res, 200, jwks);
  if (path === "/protocol/openid-connect/auth") {
    const p = url.searchParams;
    if (p.get("response_type") !== "code" || !p.get("redirect_uri") || !p.get("client_id")) {
      return json(res, 400, { error: "invalid_request" });
    }
    const code = randomBytes(16).toString("base64url");
    codes.set(code, {
      clientId: p.get("client_id"),
      redirectUri: p.get("redirect_uri"),
      nonce: p.get("nonce"),
      challenge: p.get("code_challenge"),
      authTime: now(),
    });
    const back = new URL(p.get("redirect_uri"));
    back.searchParams.set("code", code);
    if (p.get("state")) back.searchParams.set("state", p.get("state"));
    back.searchParams.set("iss", issuer);
    return redirect(res, back.toString());
  }
  if (path === "/protocol/openid-connect/token" && req.method === "POST") {
    const form = await readForm(req);
    const grant = form.get("grant_type");
    if (grant === "authorization_code") {
      const entry = codes.get(form.get("code"));
      codes.delete(form.get("code"));
      if (
        !entry ||
        entry.clientId !== form.get("client_id") ||
        entry.redirectUri !== form.get("redirect_uri")
      ) {
        return json(res, 400, { error: "invalid_grant" });
      }
      const expected = createHash("sha256")
        .update(form.get("code_verifier") ?? "")
        .digest("base64url");
      if (entry.challenge && entry.challenge !== expected)
        return json(res, 400, { error: "invalid_grant", error_description: "pkce" });
      return json(res, 200, tokens(entry));
    }
    if (grant === "refresh_token") {
      const entry = refreshTokens.get(form.get("refresh_token"));
      refreshTokens.delete(form.get("refresh_token"));
      if (!entry) return json(res, 400, { error: "invalid_grant" });
      return json(res, 200, tokens({ ...entry, nonce: undefined }));
    }
    return json(res, 400, { error: "unsupported_grant_type" });
  }
  if (path === "/protocol/openid-connect/logout") {
    const to = url.searchParams.get("post_logout_redirect_uri");
    if (to) return redirect(res, to);
    res.writeHead(200, { "content-type": "text/plain" });
    return res.end("signed out");
  }
  json(res, 404, { error: "not_found", path });
}).listen(port, "127.0.0.1", () => {
  console.log(
    `mock IdP at ${issuer} (user ${user.email}, sub ${user.sub}); JWKS at ${issuer}/protocol/openid-connect/certs`,
  );
});

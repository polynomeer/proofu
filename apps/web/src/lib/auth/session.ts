import "server-only";

import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

import { cookies } from "next/headers";

/**
 * Encrypted cookie session (AES-256-GCM, key derived from SESSION_SECRET). Two cookies keep
 * each under the 4KB limit: the long-lived session (refresh token + identity) and the short-lived
 * access token. Neither is readable by scripts.
 */
export const SESSION_COOKIE = "proofu_session";
export const ACCESS_COOKIE = "proofu_access";
export const PKCE_COOKIE = "proofu_pkce";

export type Session = {
  sub: string;
  email: string | null;
  name: string | null;
  refreshToken: string;
  /** Seconds since epoch of the last interactive login (`auth_time`). */
  authTime: number;
  /** Absolute end of this web session (ADR-0010 §5: 24h). */
  expiresAt: number;
};

export type Access = { accessToken: string; expiresAt: number };

export type PkceState = {
  state: string;
  nonce: string;
  verifier: string;
  returnTo: string;
};

const SESSION_MAX_AGE_S = 24 * 60 * 60;
const IDLE_MAX_AGE_S = 2 * 60 * 60;

function key(): Buffer {
  const secret = process.env.SESSION_SECRET;
  if (!secret || secret.length < 32)
    throw new Error("SESSION_SECRET must be at least 32 characters");
  return createHash("sha256").update(secret).digest();
}

export function seal(value: unknown): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const body = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), body]).toString("base64url");
}

export function unseal<T>(sealed: string | undefined): T | null {
  if (!sealed) return null;
  try {
    const raw = Buffer.from(sealed, "base64url");
    const decipher = createDecipheriv("aes-256-gcm", key(), raw.subarray(0, 12));
    decipher.setAuthTag(raw.subarray(12, 28));
    const body = Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]);
    return JSON.parse(body.toString("utf8")) as T;
  } catch {
    return null;
  }
}

const secure = process.env.NODE_ENV === "production";
const base = { httpOnly: true, secure, sameSite: "lax" as const, path: "/" };

export async function readSession(): Promise<Session | null> {
  const jar = await cookies();
  const session = unseal<Session>(jar.get(SESSION_COOKIE)?.value);
  if (!session || session.expiresAt * 1000 < Date.now()) return null;
  return session;
}

export async function readAccess(): Promise<Access | null> {
  const jar = await cookies();
  return unseal<Access>(jar.get(ACCESS_COOKIE)?.value);
}

/** Writes both cookies; the idle timeout is the cookie's own max-age, refreshed on every write. */
export async function writeSession(session: Session, access: Access) {
  const jar = await cookies();
  const remaining = Math.max(
    1,
    Math.min(IDLE_MAX_AGE_S, session.expiresAt - Math.floor(Date.now() / 1000)),
  );
  jar.set(SESSION_COOKIE, seal(session), { ...base, maxAge: remaining });
  jar.set(ACCESS_COOKIE, seal(access), { ...base, maxAge: remaining });
}

export async function clearSession() {
  const jar = await cookies();
  jar.delete(SESSION_COOKIE);
  jar.delete(ACCESS_COOKIE);
  jar.delete(PKCE_COOKIE);
}

export async function writePkce(state: PkceState) {
  const jar = await cookies();
  jar.set(PKCE_COOKIE, seal(state), { ...base, maxAge: 10 * 60 });
}

export async function readPkce(): Promise<PkceState | null> {
  const jar = await cookies();
  const state = unseal<PkceState>(jar.get(PKCE_COOKIE)?.value);
  jar.delete(PKCE_COOKIE);
  return state;
}

export function newSessionExpiry(): number {
  return Math.floor(Date.now() / 1000) + SESSION_MAX_AGE_S;
}

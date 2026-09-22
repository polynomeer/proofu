import "server-only";

import { AUTH_MODE, WORKSPACE_ID } from "@/lib/auth/config";
import {
  readAccess,
  readSession,
  writeSession,
  type Access,
  type Session,
} from "@/lib/auth/session";
import { refreshTokens } from "@/lib/auth/tokens";

export type Caller =
  | { mode: "header"; workspaceId: string; name: string; email: string | null }
  | { mode: "oidc"; session: Session; name: string; email: string | null };

/** Who is calling, for the shell. Null in oidc mode when there is no valid session. */
export async function currentCaller(): Promise<Caller | null> {
  if (AUTH_MODE === "header") {
    return { mode: "header", workspaceId: WORKSPACE_ID, name: "Dev User", email: null };
  }
  const session = await readSession();
  if (!session) return null;
  return {
    mode: "oidc",
    session,
    name: session.name ?? session.email ?? "사용자",
    email: session.email,
  };
}

/**
 * A usable access token for the current request, refreshing when it has under a minute left.
 * `persist` stores the refreshed tokens (only possible where cookies can be written, i.e. route
 * handlers); server components pass false and the next proxied call persists.
 */
export async function currentAccessToken(persist: boolean): Promise<string | null> {
  const session = await readSession();
  if (!session) return null;
  const access = await readAccess();
  if (access && access.expiresAt * 1000 > Date.now() + 60_000) return access.accessToken;
  try {
    const t = await refreshTokens(session.refreshToken);
    const fresh: Access = {
      accessToken: t.access_token,
      expiresAt: Math.floor(Date.now() / 1000) + t.expires_in,
    };
    if (persist) {
      await writeSession(
        { ...session, refreshToken: t.refresh_token ?? session.refreshToken },
        fresh,
      );
    }
    return fresh.accessToken;
  } catch {
    return null;
  }
}

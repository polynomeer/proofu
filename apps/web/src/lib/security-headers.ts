/**
 * Response headers for every page and BFF route (docs/security/threat-model.md). The session
 * cookie is httpOnly, but the proxy calls the API with it, so script injection is the path an
 * attacker would take: the CSP is nonce-based with no inline script allowed.
 */
export function contentSecurityPolicy(
  nonce: string,
  development = false,
  /** Origin of the OIDC provider, if one is configured: logout posts to it through /auth/logout. */
  idpOrigin?: string,
): string {
  const scriptSrc = ["'self'", `'nonce-${nonce}'`, "'strict-dynamic'"];
  const connectSrc = ["'self'"];
  if (development) {
    // next dev compiles with eval and talks to the HMR socket.
    scriptSrc.push("'unsafe-eval'");
    connectSrc.push("ws:", "wss:");
  }
  const directives: Record<string, string[]> = {
    "default-src": ["'self'"],
    // 'strict-dynamic' lets Next's own bootstrap load its chunks; nothing else may run.
    "script-src": scriptSrc,
    // Next and Tailwind emit inline <style>; those cannot be nonced reliably, and inline CSS
    // is not an execution path here.
    "style-src": ["'self'", "'unsafe-inline'", FONT_CDN],
    "font-src": ["'self'", FONT_CDN, "data:"],
    "img-src": ["'self'", "data:", "blob:"],
    "connect-src": connectSrc,
    "object-src": ["'none'"],
    "base-uri": ["'self'"],
    // Chrome applies form-action across redirects, and RP-initiated logout ends at the IdP.
    "form-action": idpOrigin ? ["'self'", idpOrigin] : ["'self'"],
    "frame-ancestors": ["'none'"],
  };
  const policy = Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(" ")}`)
    .join("; ");
  return development ? policy : `${policy}; upgrade-insecure-requests`;
}

/** Headers that do not depend on the request; set from next.config.ts for every route. */
export const STATIC_SECURITY_HEADERS: { key: string; value: string }[] = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // Nothing here uses a camera, a microphone or location.
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
  { key: "X-Permitted-Cross-Domain-Policies", value: "none" },
  ...(process.env.NODE_ENV === "production"
    ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }]
    : []),
];

const FONT_CDN = "https://cdn.jsdelivr.net";

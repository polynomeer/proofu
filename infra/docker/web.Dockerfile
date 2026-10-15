# syntax=docker/dockerfile:1
# ProofU web (Next.js BFF). Build context is the repository root.
#
#   docker build -f infra/docker/web.Dockerfile -t proofu-web .
#
# Unlike the JVM images this one builds inside the container: `next build` bakes the
# environment-independent output, and node_modules for a browser-less runtime is small enough
# to install twice.
ARG NODE_IMAGE=node:22-alpine

FROM ${NODE_IMAGE} AS build
ENV CI=1 NEXT_TELEMETRY_DISABLED=1
RUN corepack enable
WORKDIR /repo
# Manifests first: the install layer is reused until a dependency actually changes.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml turbo.json ./
COPY apps/web/package.json apps/web/
COPY packages/contracts/package.json packages/contracts/
# `web...` is web and the workspace packages it depends on — not apps/e2e, whose Playwright
# browsers have no business in a build image.
RUN pnpm install --frozen-lockfile --filter web...
COPY packages/contracts packages/contracts
COPY apps/web apps/web
RUN pnpm --filter contracts build && pnpm --filter web build

FROM ${NODE_IMAGE}
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1
WORKDIR /app
# The standalone bundle carries its own traced node_modules; the repository's are not copied.
COPY --from=build --chown=node:node /repo/apps/web/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=node:node /repo/apps/web/public ./apps/web/public
USER node

# AUTH_MODE=oidc, API_ORIGIN, APP_ORIGIN, OIDC_* and SESSION_SECRET come from the deployment;
# the app refuses to sign anyone in without them (docs/operations/deployment.md).
ENV PORT=3000 HOSTNAME=0.0.0.0
EXPOSE 3000

# A public route that renders without a session or the api.
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD wget -q --spider "http://127.0.0.1:${PORT}/auth/signed-out" || exit 1

CMD ["node", "apps/web/server.js"]

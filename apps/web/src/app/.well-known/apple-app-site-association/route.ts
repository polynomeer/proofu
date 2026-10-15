import { appSiteAssociation, iosAppIds } from "@/lib/app-links";

// Read at request time: the same image serves staging and production with different app ids.
export const dynamic = "force-dynamic";

/** 404 until an app is configured, so a deployment never claims an app it does not have. */
export function GET() {
  const appIds = iosAppIds();
  if (appIds.length === 0) return new Response(null, { status: 404 });
  return Response.json(appSiteAssociation(appIds), {
    headers: { "cache-control": "public, max-age=3600" },
  });
}

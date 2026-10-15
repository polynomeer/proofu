/**
 * The iOS app's identifiers for Universal Links (ADR-0011 §3): `IOS_APP_IDS`, comma separated,
 * each `<Team ID>.<bundle id>`. Anything that does not look like one is dropped rather than
 * published — this file tells iOS which apps may open our URLs.
 */
const APP_ID = /^[A-Z0-9]{10}\.[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;

export function iosAppIds(raw = process.env.IOS_APP_IDS): string[] {
  return (raw ?? "")
    .split(",")
    .map((id) => id.trim())
    .filter((id) => APP_ID.test(id));
}

/** Only the OIDC redirect opens the app; every other URL stays on the web. */
export function appSiteAssociation(appIds: string[]) {
  return {
    applinks: {
      details: [
        {
          appIDs: appIds,
          components: [{ "/": "/auth/ios/callback", comment: "OIDC redirect for the iOS app" }],
        },
      ],
    },
  };
}

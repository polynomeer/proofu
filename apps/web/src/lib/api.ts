import createClient from "openapi-fetch";

import { API_BASE_PATH, type paths } from "@proofu/contracts";

/**
 * Typed API client derived from packages/contracts/openapi.yaml.
 * Paths, request bodies and responses are checked against the contract at compile time.
 */
export const api = createClient<paths>({ baseUrl: API_BASE_PATH });

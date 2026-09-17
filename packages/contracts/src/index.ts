import type { components, operations, paths } from "../generated/api";

export type { components, operations, paths };

/** Shorthand for a schema in the contract, e.g. `Schema<"ProblemDetail">`. */
export type Schema<K extends keyof components["schemas"]> = components["schemas"][K];

export type ProblemDetail = Schema<"ProblemDetail">;
export type ApplicationStatus = Schema<"ApplicationStatus">;
export type JobStatus = Schema<"JobStatus">;
export type Certainty = Schema<"Certainty">;

export const API_BASE_PATH = "/api/v1" as const;

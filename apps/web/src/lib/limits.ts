/**
 * How many rows a picker loads. Pickers cannot page, so past this they say what they are
 * showing rather than hiding the rest (docs/ux/screen-specifications.md §공통 상태).
 *
 * Plain module, not a client component: a server component that imports a value from a
 * `"use client"` file gets a client reference, not the number.
 */
export const PICKER_LIMIT = 100;

/** Page size for lists that offer "더 보기". */
export const PAGE_LIMIT = 50;

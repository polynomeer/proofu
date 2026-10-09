/**
 * Filter chip links (URL-backed filters). The selected one is tinted, outlined and bold, so it
 * is told apart by more than colour; pair it with aria-current on the link.
 */
export function filterChipClass(active: boolean, size: "md" | "sm" = "md"): string {
  return [
    "inline-flex items-center rounded-md border",
    size === "sm" ? "h-8 px-2.5 text-caption" : "h-9 px-3 text-body",
    active
      ? "border-primary-600 bg-primary-050 font-semibold text-primary-700"
      : "border-border-300 bg-surface-000 text-text-900 hover:bg-surface-050",
  ].join(" ");
}

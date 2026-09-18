/** "2022.03 – 2023.12" or "2024.01 – 현재" (docs/design/accessibility.md §날짜와 숫자). */
export function formatPeriod(start: string, end: string | null | undefined): string {
  return `${formatMonth(start)} – ${end ? formatMonth(end) : "현재"}`;
}

export function formatMonth(isoDate: string): string {
  const [year, month] = isoDate.split("-");
  return `${year}.${month}`;
}

export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(
    new Date(iso),
  );
}

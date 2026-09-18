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

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(iso));
}

/** Date input value (yyyy-mm-dd) for an ISO timestamp, in local time. */
export function toDateInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** "D-3", "D-Day", "D+2" for a deadline relative to today (local time). */
export function formatDday(iso: string, now: Date = new Date()): string {
  const target = new Date(iso);
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOfDay(target) - startOfDay(now)) / 86_400_000);
  return days === 0 ? "D-Day" : days > 0 ? `D-${days}` : `D+${-days}`;
}

/** "3일 전", "오늘", "2주 전" — relative day count, coarse on purpose. */
export function formatDaysAgo(iso: string, now: Date = new Date()): string {
  const days = Math.floor((now.getTime() - new Date(iso).getTime()) / 86_400_000);
  if (days <= 0) return "오늘";
  if (days < 7) return `${days}일 전`;
  if (days < 30) return `${Math.floor(days / 7)}주 전`;
  return `${Math.floor(days / 30)}개월 전`;
}

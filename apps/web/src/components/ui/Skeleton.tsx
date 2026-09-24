/**
 * Placeholder that holds the space the real content will take, so nothing jumps when it
 * arrives (docs/ux/screen-specifications.md §공통 상태). Hidden from assistive tech; the
 * surrounding route announces loading through `aria-busy`.
 */
export function Skeleton({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`animate-pulse rounded-md bg-surface-100 motion-reduce:animate-none ${className}`}
    />
  );
}

/** The shape most list screens load into: a header block and a few rows. */
export function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div aria-busy="true" aria-live="polite" className="flex flex-col gap-4">
      <span className="sr-only">불러오는 중</span>
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-4 w-full max-w-prose" />
      <div className="flex flex-col gap-2">
        {Array.from({ length: rows }, (_, i) => (
          <Skeleton key={i} className="h-16 w-full" />
        ))}
      </div>
    </div>
  );
}

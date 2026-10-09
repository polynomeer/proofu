import type { ReactNode } from "react";

/** Explains why the list is empty and offers exactly one next action. Never seeds sample data. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-md border border-dashed border-border-300 bg-surface-000 p-4 md:p-6">
      <h2 className="text-card-title">{title}</h2>
      <p className="max-w-prose text-body text-text-600">{description}</p>
      {action}
    </div>
  );
}

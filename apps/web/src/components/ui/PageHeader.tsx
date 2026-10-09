import Link from "next/link";
import type { ReactNode } from "react";

/**
 * Page title, one-line description and the page's primary action on the right. Detail and
 * form screens pass `back` so the way up to their list sits above the title.
 */
export function PageHeader({
  title,
  description,
  action,
  back,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  back?: { href: string; label: string };
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {back ? (
          <Link
            href={back.href}
            className="mb-1 inline-block text-caption text-primary-600 hover:underline"
          >
            ← {back.label}
          </Link>
        ) : null}
        <h1 className="text-page-title">{title}</h1>
        {description ? <p className="mt-1 text-body text-text-600">{description}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

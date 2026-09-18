import type { ReactNode } from "react";

/**
 * Label above the control, required marker and help in the label area, error tied to the
 * field via aria-describedby (docs/design/components.md §입력).
 */
export function Field({
  id,
  label,
  required,
  help,
  error,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  help?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-body font-semibold">
        {label}
        {required ? (
          <span className="ml-1 text-warning-700" aria-hidden>
            *
          </span>
        ) : null}
        {help ? <span className="ml-2 font-normal text-text-600">{help}</span> : null}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-caption text-warning-700">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export const inputClass =
  "h-10 w-full rounded-md border border-border-300 bg-surface-000 px-3 text-body text-text-900 aria-[invalid=true]:border-warning-600";
export const textareaClass =
  "min-h-28 w-full rounded-md border border-border-300 bg-surface-000 px-3 py-2 text-body text-text-900 aria-[invalid=true]:border-warning-600";

import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "tertiary" | "danger";

/** Button variants from docs/design/components.md §버튼. One primary action per page. */
const VARIANT: Record<Variant, string> = {
  primary: "bg-primary-600 text-white hover:bg-primary-700 disabled:bg-primary-600/50",
  secondary:
    "border border-border-300 bg-surface-000 text-text-900 hover:bg-surface-050 disabled:text-text-600",
  tertiary: "text-primary-600 hover:bg-primary-050 disabled:text-text-600",
  danger: "border border-border-300 bg-surface-000 text-[#B42318] hover:bg-[#FDF2F1]",
};

const BASE =
  "inline-flex h-10 items-center justify-center gap-2 rounded-md px-4 text-body font-semibold whitespace-nowrap transition-colors disabled:cursor-not-allowed";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  loading?: boolean;
  children: ReactNode;
};

export function Button({
  variant = "primary",
  loading,
  children,
  className,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={[BASE, VARIANT[variant], className ?? ""].join(" ")}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : null}
      {children}
    </button>
  );
}

export function ButtonLink({
  variant = "primary",
  href,
  children,
  className,
}: {
  variant?: Variant;
  href: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link href={href} className={[BASE, VARIANT[variant], className ?? ""].join(" ")}>
      {children}
    </Link>
  );
}

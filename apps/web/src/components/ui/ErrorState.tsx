import { Icon } from "@/components/ui/Icon";

/** Says what failed and what the user can do (docs/design/accessibility.md §콘텐츠 원칙). */
export function ErrorState({ title, description }: { title: string; description: string }) {
  return (
    <div
      role="alert"
      className="flex gap-3 rounded-md border border-warning-600/40 bg-warning-050 p-4"
    >
      <Icon name="alert" className="mt-0.5 shrink-0 text-warning-700" />
      <div>
        <p className="text-card-title text-warning-700">{title}</p>
        <p className="mt-1 text-body text-text-900">{description}</p>
      </div>
    </div>
  );
}

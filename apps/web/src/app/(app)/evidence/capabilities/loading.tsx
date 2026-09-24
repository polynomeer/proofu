import { ListSkeleton } from "@/components/ui/Skeleton";

/** Held space while the list loads; detail routes render synchronously so 404s keep their status. */
export default function Loading() {
  return <ListSkeleton />;
}

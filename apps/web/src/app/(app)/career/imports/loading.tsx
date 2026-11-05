import { ListSkeleton } from "@/components/ui/Skeleton";

/** Held space while the past plans load; the wizard itself holds no server data. */
export default function Loading() {
  return <ListSkeleton />;
}

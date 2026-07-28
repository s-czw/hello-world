"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { ListView } from "@/components/list/ListView";

function ListPageInner() {
  const params = useParams<{ id: string }>();
  const id = (params?.id as string) ?? "";
  if (!id) return null;
  return <ListView projectId={id} />;
}

export default function ProjectListPage() {
  // ListView reads ?task=; Suspense satisfies useSearchParams during prerender.
  return (
    <Suspense fallback={null}>
      <ListPageInner />
    </Suspense>
  );
}

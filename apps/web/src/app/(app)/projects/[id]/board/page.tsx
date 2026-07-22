"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { ProjectHeader } from "@/components/project/ProjectHeader";
import { BoardView } from "@/components/board/BoardView";
import styles from "@/components/project/project.module.css";

function BoardPageInner() {
  const params = useParams<{ id: string }>();
  const id = (params?.id as string) ?? "";
  if (!id) return null;
  return (
    <div className={styles.wrap}>
      <ProjectHeader projectId={id} active="board" />
      <BoardView projectId={id} />
    </div>
  );
}

export default function ProjectBoardPage() {
  // BoardView reads ?task=; Suspense satisfies useSearchParams during prerender.
  return (
    <Suspense fallback={null}>
      <BoardPageInner />
    </Suspense>
  );
}

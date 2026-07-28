"use client";

import { useParams } from "next/navigation";
import { ProjectHeader } from "@/components/project/ProjectHeader";
import { OverviewView } from "@/components/project/OverviewView";
import styles from "@/components/project/project.module.css";

export default function ProjectOverviewPage() {
  const params = useParams<{ id: string }>();
  const id = (params?.id as string) ?? "";
  if (!id) return null;
  return (
    <div className={styles.wrap}>
      <ProjectHeader projectId={id} active="overview" />
      <OverviewView projectId={id} />
    </div>
  );
}

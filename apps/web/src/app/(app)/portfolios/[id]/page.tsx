"use client";

import { useMemo } from "react";
import { useParams } from "next/navigation";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { PortfolioHeader } from "@/components/portfolio/PortfolioHeader";
import { RollupSummary } from "@/components/portfolio/RollupSummary";
import { RollupTable } from "@/components/portfolio/RollupTable";
import { AddProjectsTypeahead } from "@/components/portfolio/AddProjectsTypeahead";
import { useRollup } from "@/lib/portfolio";

export default function PortfolioRollupPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const rollup = useRollup(id);
  const projects = useMemo(
    () => rollup.data?.projects ?? [],
    [rollup.data?.projects],
  );

  const existingIds = useMemo(
    () => new Set(projects.map((p) => p.projectId ?? "").filter(Boolean)),
    [projects],
  );

  const typeahead = (
    <AddProjectsTypeahead portfolioId={id} existingProjectIds={existingIds} />
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <PortfolioHeader portfolioId={id} active="overview" rightSlot={typeahead} />

      {rollup.isLoading ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <Skeleton height={96} />
          <Skeleton height={240} />
        </div>
      ) : projects.length === 0 ? (
        <EmptyState
          icon="📁"
          headline="Add projects to this portfolio"
          body="Pick projects to roll their status, progress, and schedule up here."
          action={typeahead}
        />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 16, overflow: "auto" }}>
          {rollup.data && <RollupSummary rollup={rollup.data} />}
          <RollupTable portfolioId={id} projects={projects} />
        </div>
      )}
    </div>
  );
}

"use client";

import { useParams } from "next/navigation";
import { PortfolioHeader } from "@/components/portfolio/PortfolioHeader";
import { ScheduleView } from "@/components/portfolio/ScheduleView";

export default function PortfolioSchedulePage() {
  const params = useParams<{ id: string }>();
  const id = params.id;

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <PortfolioHeader portfolioId={id} active="schedule" />
      <div style={{ overflow: "auto" }}>
        <ScheduleView portfolioId={id} />
      </div>
    </div>
  );
}

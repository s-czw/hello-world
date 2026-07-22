"use client";

import Link from "next/link";
import { usePortfolio } from "@/lib/portfolio";
import projectStyles from "@/components/project/project.module.css";

export type PortfolioTab = "overview" | "schedule";

const TABS: { key: PortfolioTab; label: string; seg: string }[] = [
  { key: "overview", label: "Overview", seg: "" },
  { key: "schedule", label: "Schedule", seg: "schedule" },
];

/**
 * Shared portfolio page header (docs/03 §4.d): breadcrumb, name + color dot,
 * Overview | Schedule view-switcher tabs, and a page-specific right slot
 * (the roll-up passes its "+ Add projects" typeahead).
 */
export function PortfolioHeader({
  portfolioId,
  active,
  rightSlot,
}: {
  portfolioId: string;
  active: PortfolioTab;
  rightSlot?: React.ReactNode;
}) {
  const portfolio = usePortfolio(portfolioId);
  const name = portfolio.data?.name ?? "Portfolio";
  const color = portfolio.data?.color;

  return (
    <div className={projectStyles.header}>
      <div className={projectStyles.headMain}>
        <div className={projectStyles.breadcrumb}>
          <Link href="/portfolios">Portfolios</Link>
        </div>
        <h1 className={projectStyles.title}>
          <span
            className={projectStyles.projectDot}
            style={color ? { background: color } : undefined}
            aria-hidden="true"
          />
          {name}
        </h1>
        <nav className={projectStyles.tabs} aria-label="Portfolio views">
          {TABS.map((t) => {
            const isActive = t.key === active;
            const href = t.seg
              ? `/portfolios/${portfolioId}/${t.seg}`
              : `/portfolios/${portfolioId}`;
            return (
              <Link
                key={t.key}
                href={href}
                className={`${projectStyles.tab} ${isActive ? projectStyles.tabActive : ""}`}
                aria-current={isActive ? "page" : undefined}
              >
                {t.label}
              </Link>
            );
          })}
        </nav>
      </div>

      {rightSlot && <div className={projectStyles.headSide}>{rightSlot}</div>}
    </div>
  );
}

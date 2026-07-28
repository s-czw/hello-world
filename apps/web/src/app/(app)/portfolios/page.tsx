"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { CreatePortfolioModal } from "@/components/portfolio/CreatePortfolioModal";
import { usePortfolios } from "@/lib/portfolio";
import pageStyles from "../page.module.css";
import styles from "@/components/portfolio/portfolio.module.css";

export default function PortfoliosPage() {
  const router = useRouter();
  const portfolios = usePortfolios();
  const [createOpen, setCreateOpen] = useState(false);

  const items = portfolios.data ?? [];

  return (
    <div className={pageStyles.page}>
      <div className={pageStyles.pageHeader}>
        <div>
          <h1 className={pageStyles.pageTitle}>Portfolios</h1>
          <p className={pageStyles.pageSub}>
            Roll projects up into one status view.
          </p>
        </div>
        {items.length > 0 && (
          <Button onClick={() => setCreateOpen(true)}>New portfolio</Button>
        )}
      </div>

      {portfolios.isLoading ? (
        <div className={styles.grid}>
          <Skeleton height={96} />
          <Skeleton height={96} />
          <Skeleton height={96} />
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon="📊"
          headline="Portfolios roll projects up into one status view"
          body="Group related projects to track status, progress, and schedule in one place."
          action={
            <Button onClick={() => setCreateOpen(true)}>New portfolio</Button>
          }
        />
      ) : (
        <div className={styles.grid}>
          {items.map((p) => (
            <Link key={p.id} href={`/portfolios/${p.id}`} className={styles.card}>
              <div className={styles.cardName}>
                <span
                  className={styles.dot}
                  style={p.color ? { background: p.color } : undefined}
                  aria-hidden="true"
                />
                {p.name}
              </div>
              {p.description && <div className={styles.cardDesc}>{p.description}</div>}
              <div className={styles.cardMeta}>View roll-up →</div>
            </Link>
          ))}
        </div>
      )}

      <CreatePortfolioModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(portfolio) => {
          setCreateOpen(false);
          router.push(`/portfolios/${portfolio.id}`);
        }}
      />
    </div>
  );
}

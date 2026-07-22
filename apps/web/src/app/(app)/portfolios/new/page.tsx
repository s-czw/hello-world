"use client";

import { useRouter } from "next/navigation";
import { CreatePortfolioModal } from "@/components/portfolio/CreatePortfolioModal";

/** Modal route for the sidebar "+ New" — opens the create modal over the app. */
export default function NewPortfolioPage() {
  const router = useRouter();
  return (
    <CreatePortfolioModal
      open
      onClose={() => router.back()}
      onCreated={(portfolio) => router.replace(`/portfolios/${portfolio.id}`)}
    />
  );
}

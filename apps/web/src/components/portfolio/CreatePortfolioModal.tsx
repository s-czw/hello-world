"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { useCreatePortfolio, type Portfolio } from "@/lib/portfolio";
import pageStyles from "@/app/(app)/page.module.css";

const COLORS = [
  "#4F46E5",
  "#0E7490",
  "#188A42",
  "#B45309",
  "#BE185D",
  "#7C3AED",
  "#B42328",
  "#334155",
];

export interface CreatePortfolioModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (portfolio: Portfolio) => void;
}

/**
 * Create-portfolio modal (name / description / color). Shared by the /portfolios
 * page ("New portfolio" button) and the /portfolios/new route (sidebar "+").
 */
export function CreatePortfolioModal({
  open,
  onClose,
  onCreated,
}: CreatePortfolioModalProps) {
  const create = useCreatePortfolio();
  const [name, setName] = useState("");
  const [color, setColor] = useState(COLORS[0]);
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError("A name is required.");
      return;
    }
    const portfolio = await create.mutateAsync({
      name: name.trim(),
      description: description.trim() || undefined,
      color,
    });
    if (portfolio?.id) onCreated(portfolio);
  }

  return (
    <Modal open={open} onClose={onClose} title="New portfolio">
      <form className={pageStyles.newProjectForm} onSubmit={onSubmit}>
        {error && (
          <div className={pageStyles.formError} role="alert">
            {error}
          </div>
        )}
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          autoFocus
          modal
          placeholder="e.g. Q3 Operations"
        />
        <div className={pageStyles.field}>
          <span className={pageStyles.fieldLabel}>Color</span>
          <div className={pageStyles.swatchRow}>
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                className={pageStyles.swatch}
                aria-label={`Color ${c}`}
                aria-pressed={c === color}
                data-selected={c === color}
                style={{ background: c }}
                onClick={() => setColor(c)}
              />
            ))}
          </div>
        </div>
        <Input
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          modal
        />
        <div className={pageStyles.newProjectFooter}>
          <Button variant="secondary" size="dialog" onClick={onClose} type="button">
            Cancel
          </Button>
          <Button size="dialog" type="submit" loading={create.isPending}>
            Create portfolio
          </Button>
        </div>
      </form>
    </Modal>
  );
}

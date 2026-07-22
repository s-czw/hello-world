"use client";

import { useEffect, useState } from "react";
import { format } from "date-fns";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { STATUSES, type ProjectStatus } from "@/lib/status";
import { usePostStatusUpdate, useStatusUpdates } from "@/lib/project";
import styles from "./project.module.css";

export interface StatusComposerProps {
  open: boolean;
  onClose: () => void;
  projectId: string;
  /** Preselect this status (e.g. the project's current status, or a clicked chip). */
  initialStatus?: string | null;
}

function defaultTitle(): string {
  return `Status update — ${format(new Date(), "MMM d, yyyy")}`;
}

/**
 * The spec D3 status-composer ritual (docs/03 §2.6 / §4.d): 4-color picker with
 * the On-hold helper copy, prefilled title, plain-text body, and a "Copy
 * previous update" button that prefills the body from the latest history entry
 * (PD-13). Posts to /projects/{id}/status-updates.
 */
export function StatusComposer({
  open,
  onClose,
  projectId,
  initialStatus,
}: StatusComposerProps) {
  const post = usePostStatusUpdate(projectId);
  const history = useStatusUpdates(projectId);
  const previous = history.data?.[0];

  const [status, setStatus] = useState<ProjectStatus>("on_track");
  const [title, setTitle] = useState(defaultTitle());
  const [body, setBody] = useState("");

  // Reset the form each time the composer opens.
  useEffect(() => {
    if (!open) return;
    const preset = STATUSES.find((s) => s.value === initialStatus)?.value;
    setStatus(preset ?? "on_track");
    setTitle(defaultTitle());
    setBody("");
  }, [open, initialStatus]);

  const selected = STATUSES.find((s) => s.value === status)!;

  async function submit() {
    if (post.isPending) return;
    await post.mutateAsync(
      { status, title: title.trim() || defaultTitle(), body: body.trim() },
      { onSuccess: () => onClose() },
    );
  }

  function copyPrevious() {
    if (previous) setBody(previous.body ?? "");
  }

  return (
    <Modal open={open} onClose={onClose} title="Update status">
      <div className={styles.composer}>
        <div className={styles.field}>
          <span className={styles.fieldLabel} id={`status-lbl-${projectId}`}>
            Status
          </span>
          <div
            className={styles.statusPicker}
            role="radiogroup"
            aria-labelledby={`status-lbl-${projectId}`}
          >
            {STATUSES.map((s) => {
              const active = s.value === status;
              return (
                <button
                  key={s.value}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  className={styles.statusOption}
                  data-active={active}
                  style={{
                    background: active ? s.bg : "transparent",
                    color: active ? s.ink : "var(--ink-secondary)",
                    borderColor: active ? s.bold : "var(--border-default)",
                  }}
                  onClick={() => setStatus(s.value)}
                >
                  <span aria-hidden="true" style={{ color: s.bold, fontSize: 9 }}>
                    {s.icon}
                  </span>
                  {s.label}
                </button>
              );
            })}
          </div>
          {selected.helper && (
            <p className={styles.statusHelper}>{selected.helper}</p>
          )}
        </div>

        <div className={styles.field}>
          <label className={styles.fieldLabel} htmlFor={`status-title-${projectId}`}>
            Title
          </label>
          <input
            id={`status-title-${projectId}`}
            className={styles.composerInput}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>

        <div className={styles.field}>
          <div className={styles.bodyLabelRow}>
            <label className={styles.fieldLabel} htmlFor={`status-body-${projectId}`}>
              Update
            </label>
            <button
              type="button"
              className={styles.copyPrevBtn}
              onClick={copyPrevious}
              disabled={!previous}
              title={
                previous
                  ? "Prefill the body from the last update"
                  : "No previous update to copy"
              }
            >
              Copy previous update
            </button>
          </div>
          <textarea
            id={`status-body-${projectId}`}
            className={styles.composerTextarea}
            value={body}
            rows={5}
            placeholder="What's the latest? (plain text — URLs auto-link)"
            onChange={(e) => setBody(e.target.value)}
          />
        </div>
      </div>

      <div className={styles.composerFooter}>
        <Button variant="secondary" size="dialog" onClick={onClose}>
          Cancel
        </Button>
        <Button size="dialog" onClick={submit} loading={post.isPending}>
          Post update
        </Button>
      </div>
    </Modal>
  );
}

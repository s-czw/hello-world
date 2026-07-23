"use client";

import { useEffect, useRef, useState } from "react";
import { formatBytes, isImageType, type Attachment } from "@/lib/taskdetail";
import styles from "./task.module.css";

export interface AttachmentsProps {
  attachments: Attachment[];
  uploading: number;
  onUpload: (files: File[]) => void;
  onDelete: (id: string) => void;
  canDelete: (a: Attachment) => boolean;
}

/**
 * Attachments (docs/03 §4.c-6): chip list with name + size; image attachments
 * show a thumbnail that opens a lightbox; others download through the authz'd
 * `downloadUrl` (the API forces `Content-Disposition: attachment`). "Attach"
 * button + the whole-drawer drop target (owned by TaskPeek) feed `onUpload`.
 */
export function Attachments({
  attachments,
  uploading,
  onUpload,
  onDelete,
  canDelete,
}: AttachmentsProps) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [lightbox, setLightbox] = useState<Attachment | null>(null);

  useEffect(() => {
    if (!lightbox) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        setLightbox(null);
      }
    }
    document.addEventListener("keydown", onKey, true);
    return () => document.removeEventListener("keydown", onKey, true);
  }, [lightbox]);

  const empty = attachments.length === 0 && uploading === 0;

  return (
    <section className={styles.block}>
      <div className={styles.blockHead}>
        <div className={styles.peekSectionTitle}>Attachments</div>
        <button
          type="button"
          className={styles.attachBtn}
          onClick={() => fileRef.current?.click()}
        >
          Attach
        </button>
        <input
          ref={fileRef}
          type="file"
          multiple
          className={styles.srInput}
          aria-label="Attach files"
          onChange={(e) => {
            const files = Array.from(e.target.files ?? []);
            if (files.length) onUpload(files);
            e.target.value = "";
          }}
        />
      </div>

      {empty ? (
        <p className={styles.attachEmpty}>Drop files here or use Attach.</p>
      ) : (
        <ul className={styles.attachList}>
          {attachments.map((a) => {
            const img = isImageType(a.contentType);
            return (
              <li key={a.id} className={styles.attachChip}>
                {img ? (
                  <button
                    type="button"
                    className={styles.attachThumbBtn}
                    onClick={() => setLightbox(a)}
                    aria-label={`Preview ${a.fileName}`}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img className={styles.attachThumb} src={a.downloadUrl} alt="" />
                  </button>
                ) : (
                  <span className={styles.attachIcon} aria-hidden="true">
                    📄
                  </span>
                )}
                <a
                  className={styles.attachMeta}
                  href={a.downloadUrl}
                  download={a.fileName}
                  title={`Download ${a.fileName}`}
                >
                  <span className={styles.attachName}>{a.fileName}</span>
                  <span className={styles.attachSize}>{formatBytes(a.sizeBytes)}</span>
                </a>
                {canDelete(a) && (
                  <button
                    type="button"
                    className={styles.attachRemove}
                    aria-label={`Remove ${a.fileName}`}
                    onClick={() => onDelete(a.id ?? "")}
                  >
                    ✕
                  </button>
                )}
              </li>
            );
          })}
          {uploading > 0 && (
            <li className={`${styles.attachChip} ${styles.attachUploading}`}>
              <span className={styles.spinnerSm} aria-hidden="true" />
              <span className={styles.attachName}>
                Uploading {uploading} file{uploading > 1 ? "s" : ""}…
              </span>
            </li>
          )}
        </ul>
      )}

      {lightbox && (
        <div
          className={styles.lightbox}
          role="dialog"
          aria-modal="true"
          aria-label={lightbox.fileName}
          onClick={() => setLightbox(null)}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            className={styles.lightboxImg}
            src={lightbox.downloadUrl}
            alt={lightbox.fileName}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            type="button"
            className={styles.lightboxClose}
            aria-label="Close preview"
            onClick={() => setLightbox(null)}
          >
            ✕
          </button>
        </div>
      )}
    </section>
  );
}

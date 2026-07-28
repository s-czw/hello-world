"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Avatar } from "@/components/ui/Avatar";
import { useUsers, type User } from "@/lib/tasks";
import styles from "./task.module.css";

export interface CommentComposerProps {
  onSubmit: (body: string) => void;
  onAttach: (files: File[]) => void;
  onDraftChange?: (hasDraft: boolean) => void;
}

interface Mention {
  start: number;
  query: string;
}

/** Find an in-progress `@mention` ending at the caret, or null. */
function detectMention(text: string, caret: number): Mention | null {
  const upto = text.slice(0, caret);
  const at = upto.lastIndexOf("@");
  if (at < 0) return null;
  if (at > 0 && !/\s/.test(text[at - 1])) return null; // must start a word
  const frag = upto.slice(at + 1);
  if (frag.length > 40 || /\s/.test(frag)) return null;
  return { start: at, query: frag };
}

/**
 * Comment composer pinned at the bottom of the peek (docs/03 §4.c-7): plain text
 * (D-011), @mention typeahead (inserts an `@<email>` token the API resolves),
 * an attach button, and Ctrl/Cmd+Enter to send. Plain Enter inserts a newline.
 */
export function CommentComposer({ onSubmit, onAttach, onDraftChange }: CommentComposerProps) {
  const { data: users } = useUsers();
  const [value, setValue] = useState("");
  const [mention, setMention] = useState<Mention | null>(null);
  const [sel, setSel] = useState(0);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    onDraftChange?.(value.trim().length > 0);
  }, [value, onDraftChange]);

  const matches = useMemo(() => {
    if (!mention) return [];
    const q = mention.query.toLowerCase();
    const active = (users ?? []).filter((u) => u.active !== false);
    const list = q
      ? active.filter(
          (u) =>
            (u.name ?? "").toLowerCase().includes(q) ||
            (u.email ?? "").toLowerCase().includes(q),
        )
      : active;
    return list.slice(0, 6);
  }, [mention, users]);

  const open = mention !== null && matches.length > 0;

  function refreshMention(el: HTMLTextAreaElement) {
    const m = detectMention(el.value, el.selectionStart ?? el.value.length);
    setMention(m);
    setSel(0);
  }

  function accept(u: User) {
    const el = taRef.current;
    if (!el || !mention) return;
    const caret = el.selectionStart ?? value.length;
    const token = `@${u.email ?? u.id} `;
    const before = value.slice(0, mention.start);
    const after = value.slice(caret);
    const next = before + token + after;
    setValue(next);
    setMention(null);
    const pos = (before + token).length;
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(pos, pos);
    });
  }

  function submit() {
    const body = value.trim();
    if (!body) return;
    onSubmit(body);
    setValue("");
    setMention(null);
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (open) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        setSel((s) => (s + 1) % matches.length);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        setSel((s) => (s - 1 + matches.length) % matches.length);
        return;
      }
      if (e.key === "Enter" || e.key === "Tab") {
        e.preventDefault();
        accept(matches[sel]);
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation(); // don't let the drawer close
        setMention(null);
        return;
      }
    }
    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <div className={styles.composer}>
      {open && (
        <ul className={styles.mentionList} role="listbox" aria-label="Mention someone">
          {matches.map((u, i) => (
            <li key={u.id}>
              <button
                type="button"
                role="option"
                aria-selected={i === sel}
                className={`${styles.mentionOption} ${i === sel ? styles.mentionOptionSel : ""}`}
                // onMouseDown (not onClick) so the textarea keeps focus
                onMouseDown={(e) => {
                  e.preventDefault();
                  accept(u);
                }}
              >
                <Avatar name={u.name ?? "?"} seed={u.id} size={20} />
                <span className={styles.mentionName}>{u.name}</span>
                <span className={styles.mentionEmail}>{u.email}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className={styles.composerRow}>
        <button
          type="button"
          className={styles.composerAttach}
          aria-label="Attach a file"
          title="Attach a file"
          onClick={() => fileRef.current?.click()}
        >
          📎
        </button>
        <input
          ref={fileRef}
          type="file"
          multiple
          className={styles.srInput}
          aria-label="Attach files to this task"
          onChange={(e) => {
            const files = Array.from(e.target.files ?? []);
            if (files.length) onAttach(files);
            e.target.value = "";
          }}
        />
        <textarea
          ref={taRef}
          className={styles.composerInput}
          placeholder="Add a comment…  (Ctrl+Enter to send, @ to mention)"
          aria-label="Add a comment"
          rows={1}
          value={value}
          onChange={(e) => {
            setValue(e.target.value);
            refreshMention(e.target);
            e.target.style.height = "auto";
            e.target.style.height = `${Math.min(e.target.scrollHeight, 140)}px`;
          }}
          onClick={(e) => refreshMention(e.currentTarget)}
          onKeyUp={(e) => {
            if (e.key.startsWith("Arrow") || e.key === "Home" || e.key === "End") {
              refreshMention(e.currentTarget);
            }
          }}
          onKeyDown={onKeyDown}
        />
        <button
          type="button"
          className={styles.composerSend}
          disabled={value.trim().length === 0}
          onClick={submit}
        >
          Send
        </button>
      </div>
    </div>
  );
}

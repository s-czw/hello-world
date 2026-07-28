"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useToast } from "@/components/ui/Toast";
import { playPeekSwap } from "@/lib/motion";
import { linkify } from "@/lib/linkify";
import { isAdmin, useMe } from "@/lib/auth";
import { useUsers, type Priority, type Section, type Task } from "@/lib/tasks";
import {
  useActivityStream,
  useAttachments,
  useTaskDetail,
  useTaskDetailMutations,
  type Attachment,
} from "@/lib/taskdetail";
import { CompleteToggle } from "./CompleteToggle";
import { AssigneePicker } from "./AssigneePicker";
import { PriorityPicker } from "./PriorityPicker";
import { SectionPicker } from "./SectionPicker";
import { SubtaskList } from "./SubtaskList";
import { Attachments } from "./Attachments";
import { ActivityStream } from "./ActivityStream";
import { CommentComposer } from "./CommentComposer";
import { DatePicker } from "@/components/ui/DatePicker";
import uiStyles from "@/components/ui/ui.module.css";
import styles from "./task.module.css";

export interface TaskPeekProps {
  task: Task;
  sections: Section[];
  projectId: string;
  onClose: () => void;
  /** Open another task in the list peek (used after Promote-to-task). */
  onOpenTask?: (id: string) => void;
}

/** Task detail side-peek (docs/03 §4.c) — fields, subtasks, attachments, comments/activity. */
export function TaskPeek({ task, sections, projectId, onClose, onOpenTask }: TaskPeekProps) {
  const toast = useToast();
  const { data: me } = useMe();
  const { data: users } = useUsers();
  const m = useTaskDetailMutations(projectId);

  const bodyRef = useRef<HTMLDivElement>(null);
  const propIdRef = useRef<string | undefined>(task.id);

  // The viewed task: starts at the list task; drilling into a subtask retargets it.
  const [activeId, setActiveId] = useState<string>(task.id ?? "");
  const [seed, setSeed] = useState<Task>(task);

  const detailQ = useTaskDetail(activeId, seed.id === activeId ? seed : undefined);
  const activityQ = useActivityStream(activeId);
  const attachmentsQ = useAttachments(activeId);

  const cur: Task = detailQ.data?.task ?? seed;
  const subtasks = detailQ.data?.subtasks ?? [];
  const rawProgress = detailQ.data?.subtaskProgress;
  const progress = { total: rawProgress?.total ?? 0, completed: rawProgress?.completed ?? 0 };
  const isSubtaskView = !!cur.parentTaskId;

  const [title, setTitle] = useState(cur.title ?? "");
  const [desc, setDesc] = useState(cur.description ?? "");
  const [editingDesc, setEditingDesc] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [uploading, setUploading] = useState(0);
  const [dragOver, setDragOver] = useState(false);
  const dragDepth = useRef(0);
  const hasDraftRef = useRef(false);

  // Reset when the list retargets the peek (↑/↓ or a new open).
  useEffect(() => {
    if (propIdRef.current !== task.id) {
      propIdRef.current = task.id;
      setActiveId(task.id ?? "");
      setSeed(task);
    }
  }, [task]);

  // Sync local editable fields + crossfade when the viewed task changes.
  useEffect(() => {
    setTitle(cur.title ?? "");
    setDesc(cur.description ?? "");
    setEditingDesc(false);
    setMenuOpen(false);
    if (bodyRef.current) playPeekSwap(bodyRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  const sectionNames = useMemo(() => {
    const map: Record<string, string> = {};
    for (const s of sections) if (s.id) map[s.id] = s.name ?? "Untitled section";
    return map;
  }, [sections]);

  const patch = (p: Parameters<typeof m.patchTask.mutate>[0]["patch"]) =>
    m.patchTask.mutate({ id: activeId, parentId: cur.parentTaskId, patch: p });

  function commitTitle() {
    const t = title.trim();
    if (t === "" || t === (cur.title ?? "")) {
      setTitle(cur.title ?? "");
      return;
    }
    patch({ title: t });
  }

  function commitDesc() {
    setEditingDesc(false);
    if (desc === (cur.description ?? "")) return;
    patch({ description: desc });
  }

  const handleUpload = useCallback(
    async (files: File[]) => {
      setUploading((n) => n + files.length);
      await Promise.all(
        files.map((file) =>
          m.uploadAttachment
            .mutateAsync({ taskId: activeId, file })
            .catch(() => {})
            .finally(() => setUploading((n) => Math.max(0, n - 1))),
        ),
      );
    },
    [m.uploadAttachment, activeId],
  );

  function requestClose() {
    if (hasDraftRef.current && !confirm("Discard your unsent comment?")) return;
    onClose();
  }

  function drillInto(id: string) {
    const s = subtasks.find((x) => x.id === id);
    if (s) setSeed(s);
    setActiveId(id);
  }
  function backToParent() {
    setSeed(task);
    setActiveId(task.id ?? "");
  }

  function promote() {
    m.promoteSubtask
      .mutateAsync({ id: activeId, parentId: task.id ?? "", sectionId: task.sectionId ?? null })
      .then((promoted) => {
        if (promoted?.id && onOpenTask) onOpenTask(promoted.id);
        else backToParent();
      })
      .catch(() => {});
  }

  function del() {
    setMenuOpen(false);
    if (!confirm("Delete this task? This cannot be undone.")) return;
    m.deleteTask.mutate({ id: activeId, parentId: cur.parentTaskId });
    if (isSubtaskView) backToParent();
    else onClose();
  }

  const canDeleteAttachment = useCallback(
    (a: Attachment) => a.uploadedBy === me?.id || isAdmin(me),
    [me],
  );

  // Native file drag-and-drop onto the whole drawer.
  function onDragEnter(e: React.DragEvent) {
    if (!Array.from(e.dataTransfer.types).includes("Files")) return;
    dragDepth.current += 1;
    setDragOver(true);
  }
  function onDragLeave(e: React.DragEvent) {
    if (!Array.from(e.dataTransfer.types).includes("Files")) return;
    dragDepth.current = Math.max(0, dragDepth.current - 1);
    if (dragDepth.current === 0) setDragOver(false);
  }
  function onDrop(e: React.DragEvent) {
    if (!Array.from(e.dataTransfer.types).includes("Files")) return;
    e.preventDefault();
    dragDepth.current = 0;
    setDragOver(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length) void handleUpload(files);
  }

  return (
    <div
      className={styles.peek}
      onDragEnter={onDragEnter}
      onDragLeave={onDragLeave}
      onDragOver={(e) => {
        if (Array.from(e.dataTransfer.types).includes("Files")) e.preventDefault();
      }}
      onDrop={onDrop}
    >
      {dragOver && (
        <div className={styles.dropOverlay} aria-hidden="true">
          <span>Drop to attach</span>
        </div>
      )}

      <div className={styles.peekHeader}>
        <CompleteToggle
          taskId={activeId}
          completed={!!cur.completed}
          onChange={(c) => patch({ completed: c })}
        />
        <span className={styles.peekCompleteLabel}>
          {cur.completed ? "Completed" : "Mark complete"}
        </span>
        <div className={styles.peekActions}>
          <button
            type="button"
            className={uiStyles.iconBtn}
            aria-label="Copy link to task"
            title="Copy link"
            onClick={() => {
              navigator.clipboard
                ?.writeText(window.location.href)
                .then(() => toast.success("Link copied"))
                .catch(() => toast.error("Couldn't copy the link"));
            }}
          >
            🔗
          </button>
          <button
            type="button"
            className={uiStyles.iconBtn}
            aria-label="More actions"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}
          >
            ⋯
          </button>
          {menuOpen && (
            <div className={styles.menu} role="menu">
              <button
                type="button"
                role="menuitem"
                className={`${styles.menuItem} ${styles.menuItemDanger}`}
                onClick={del}
              >
                Delete task
              </button>
            </div>
          )}
          <button type="button" className={uiStyles.iconBtn} aria-label="Close" onClick={requestClose}>
            ✕
          </button>
        </div>
      </div>

      <div className={styles.peekScroll} ref={bodyRef}>
        {isSubtaskView && (
          <nav className={styles.breadcrumb} aria-label="Breadcrumb">
            <button type="button" className={styles.breadcrumbBtn} onClick={backToParent}>
              ← {task.title || "Parent task"}
            </button>
            <button type="button" className={styles.promoteBtn} onClick={promote}>
              Promote to task
            </button>
          </nav>
        )}

        <textarea
          className={`${styles.peekTitle} ${cur.completed ? styles.peekTitleDone : ""}`}
          value={title}
          rows={1}
          aria-label="Task title"
          onChange={(e) => {
            setTitle(e.target.value);
            e.target.style.height = "auto";
            e.target.style.height = `${e.target.scrollHeight}px`;
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              (e.target as HTMLTextAreaElement).blur();
            }
          }}
          onBlur={commitTitle}
        />

        <div className={styles.peekGrid}>
          <span className={styles.peekLabel}>Assignee</span>
          <AssigneePicker value={cur.assigneeId} onChange={(id) => patch({ assigneeId: id })} />

          <span className={styles.peekLabel}>Due date</span>
          <DatePicker value={cur.dueDate} onChange={(iso) => patch({ dueDate: iso })} />

          <span className={styles.peekLabel}>Priority</span>
          <PriorityPicker value={cur.priority} onChange={(p: Priority) => patch({ priority: p })} />

          {!isSubtaskView && (
            <>
              <span className={styles.peekLabel}>Section</span>
              <SectionPicker
                value={cur.sectionId}
                sections={sections}
                onChange={(sid) => m.moveViewedTask.mutate({ id: activeId, sectionId: sid })}
              />
            </>
          )}
        </div>

        <div>
          <div className={styles.peekSectionTitle}>Description</div>
          {editingDesc ? (
            <textarea
              className={styles.peekDesc}
              value={desc}
              autoFocus
              aria-label="Description"
              placeholder="Add a description…"
              onChange={(e) => setDesc(e.target.value)}
              onBlur={commitDesc}
            />
          ) : (
            <div
              className={styles.descDisplay}
              role="button"
              tabIndex={0}
              onClick={() => setEditingDesc(true)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setEditingDesc(true);
                }
              }}
            >
              {desc ? linkify(desc) : <span className={styles.descPlaceholder}>Add a description…</span>}
            </div>
          )}
        </div>

        {!isSubtaskView && (
          <SubtaskList
            parentId={activeId}
            subtasks={subtasks}
            progress={progress}
            onOpenSubtask={drillInto}
            onToggleSubtask={(id, completed) =>
              m.patchTask.mutate({ id, parentId: activeId, patch: { completed } })
            }
            onAddSubtask={(t) => m.addSubtask.mutate({ parentId: activeId, title: t })}
          />
        )}

        <Attachments
          attachments={attachmentsQ.data ?? []}
          uploading={uploading}
          onUpload={handleUpload}
          onDelete={(id) => m.deleteAttachment.mutate({ attachmentId: id, taskId: activeId })}
          canDelete={canDeleteAttachment}
        />

        <ActivityStream
          entries={activityQ.data ?? []}
          users={users ?? []}
          meId={me?.id}
          isAdmin={isAdmin(me)}
          sectionNames={sectionNames}
          onEditComment={(cid, body) => m.editComment.mutate({ commentId: cid, taskId: activeId, body })}
          onDeleteComment={(cid) => m.deleteComment.mutate({ commentId: cid, taskId: activeId })}
        />
      </div>

      <CommentComposer
        onSubmit={(body) => m.postComment.mutate({ taskId: activeId, body })}
        onAttach={handleUpload}
        onDraftChange={(has) => {
          hasDraftRef.current = has;
        }}
      />
    </div>
  );
}

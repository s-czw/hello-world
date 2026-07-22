"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  closestCorners,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { Drawer } from "@/components/ui/Drawer";
import { anyPopoverOpen } from "@/components/ui/Popover";
import { TaskPeek } from "@/components/task/TaskPeek";
import { SectionGroup } from "./SectionGroup";
import {
  useProject,
  useSections,
  useTasks,
  useTaskMutations,
  type Task,
  type TaskPatch,
} from "@/lib/tasks";
import styles from "./list.module.css";

const NONE = "__none__";

interface Model {
  groupKeys: string[];
  groupNames: Record<string, string>;
  taskMap: Map<string, Task>;
  columns: Record<string, string[]>;
  completedByKey: Record<string, Task[]>;
}

function buildModel(tasks: Task[], sectionIds: string[], sectionNames: Record<string, string>): Model {
  const known = new Set(sectionIds);
  const keyOf = (t: Task) =>
    t.sectionId && known.has(t.sectionId) ? t.sectionId : NONE;

  const taskMap = new Map<string, Task>();
  for (const t of tasks) if (t.id) taskMap.set(t.id, t);

  const groupKeys = [...sectionIds];
  const groupNames: Record<string, string> = { ...sectionNames };
  const hasNone = tasks.some((t) => keyOf(t) === NONE);
  if (hasNone || sectionIds.length === 0) {
    groupKeys.push(NONE);
    groupNames[NONE] = "No section";
  }

  const columns: Record<string, string[]> = {};
  const completedByKey: Record<string, Task[]> = {};
  for (const k of groupKeys) {
    columns[k] = [];
    completedByKey[k] = [];
  }
  for (const t of tasks) {
    if (!t.id) continue;
    const k = keyOf(t);
    if (!columns[k]) {
      columns[k] = [];
      completedByKey[k] = [];
    }
    if (t.completed) completedByKey[k].push(t);
    else columns[k].push(t.id);
  }
  return { groupKeys, groupNames, taskMap, columns, completedByKey };
}

export function ListView({ projectId }: { projectId: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const openTaskId = searchParams.get("task");

  const project = useProject(projectId);
  const sectionsQ = useSections(projectId);
  const tasksQ = useTasks(projectId);
  const { updateTask, moveTask, deleteTask, createTask } = useTaskMutations(projectId);

  const sections = useMemo(() => sectionsQ.data ?? [], [sectionsQ.data]);
  const tasks = useMemo(() => tasksQ.data ?? [], [tasksQ.data]);

  const model = useMemo(() => {
    const ids = sections.map((s) => s.id ?? "").filter(Boolean);
    const names: Record<string, string> = {};
    for (const s of sections) if (s.id) names[s.id] = s.name ?? "Untitled section";
    return buildModel(tasks, ids, names);
  }, [tasks, sections]);

  // Local working copy of incomplete-task order per group (drag source of truth).
  const [columns, setColumns] = useState<Record<string, string[]>>(model.columns);
  const columnsRef = useRef<Record<string, string[]>>(model.columns);
  const draggingRef = useRef(false);
  const [activeId, setActiveId] = useState<string | null>(null);

  const commitColumns = useCallback((next: Record<string, string[]>) => {
    columnsRef.current = next;
    setColumns(next);
  }, []);

  useEffect(() => {
    if (!draggingRef.current) commitColumns(model.columns);
  }, [model, commitColumns]);

  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const [completedExpanded, setCompletedExpanded] = useState<Record<string, boolean>>({});
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const colFor = useCallback(
    (k: string) => columns[k] ?? model.columns[k] ?? [],
    [columns, model],
  );

  // ── DnD ────────────────────────────────────────────────────────────────
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
  );

  const keyOfId = (cols: Record<string, string[]>, id: string): string | null => {
    if (cols[id]) return id; // id is a container key
    for (const k of Object.keys(cols)) if (cols[k].includes(id)) return k;
    return null;
  };

  function onDragStart(e: DragStartEvent) {
    draggingRef.current = true;
    setActiveId(String(e.active.id));
  }

  function onDragOver(e: DragOverEvent) {
    const { active, over } = e;
    if (!over) return;
    const cols = columnsRef.current;
    const from = keyOfId(cols, String(active.id));
    const to = keyOfId(cols, String(over.id));
    if (!from || !to || from === to) return;
    const next = { ...cols };
    const fromList = [...next[from]];
    const toList = [...next[to]];
    const ai = fromList.indexOf(String(active.id));
    if (ai < 0) return;
    fromList.splice(ai, 1);
    const overIsContainer = !!cols[String(over.id)];
    const idx = overIsContainer ? toList.length : Math.max(0, toList.indexOf(String(over.id)));
    toList.splice(idx, 0, String(active.id));
    next[from] = fromList;
    next[to] = toList;
    commitColumns(next);
  }

  function fireMove(id: string, key: string, list: string[]) {
    const pos = list.indexOf(id);
    const afterId = pos > 0 ? list[pos - 1] : null;
    const beforeId = pos >= 0 && pos < list.length - 1 ? list[pos + 1] : null;
    moveTask.mutate({
      id,
      sectionId: key === NONE ? null : key,
      afterTaskId: afterId ?? undefined,
      beforeTaskId: afterId ? undefined : beforeId ?? undefined,
    });
  }

  function onDragEnd(e: DragEndEvent) {
    draggingRef.current = false;
    setActiveId(null);
    const { active, over } = e;
    if (!over) {
      commitColumns(model.columns);
      return;
    }
    const cols = columnsRef.current;
    const id = String(active.id);
    const key = keyOfId(cols, String(over.id)) ?? keyOfId(cols, id);
    if (!key) {
      commitColumns(model.columns);
      return;
    }
    let next = cols;
    const overIsContainer = !!cols[String(over.id)];
    if (!overIsContainer && String(over.id) !== id) {
      const list = [...cols[key]];
      const oi = list.indexOf(id);
      const ni = list.indexOf(String(over.id));
      if (oi >= 0 && ni >= 0 && oi !== ni) {
        next = { ...cols, [key]: arrayMove(list, oi, ni) };
        commitColumns(next);
      }
    }
    fireMove(id, key, next[key] ?? []);
  }

  // ── Row action handlers ──────────────────────────────────────────────────
  const onUpdate = useCallback(
    (id: string, patch: TaskPatch) => updateTask.mutate({ id, patch }),
    [updateTask],
  );
  const onComplete = useCallback(
    (id: string, completed: boolean) => updateTask.mutate({ id, patch: { completed } }),
    [updateTask],
  );
  const onDelete = useCallback(
    (id: string) => {
      deleteTask.mutate(id);
      if (openTaskId === id) router.push(pathname);
    },
    [deleteTask, openTaskId, pathname, router],
  );

  const onMoveToSection = useCallback(
    (id: string, sectionId: string | null) => {
      const target = sectionId ?? NONE;
      const cur = columnsRef.current;
      const next: Record<string, string[]> = {};
      for (const k of Object.keys(cur)) next[k] = cur[k].filter((x) => x !== id);
      if (!next[target]) next[target] = [];
      next[target] = [...next[target], id];
      commitColumns(next);
      moveTask.mutate({ id, sectionId });
    },
    [commitColumns, moveTask],
  );

  const onMoveWithin = useCallback(
    (id: string, dir: -1 | 1) => {
      const cur = columnsRef.current;
      const key = keyOfId(cur, id);
      if (!key) return;
      const list = [...cur[key]];
      const i = list.indexOf(id);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= list.length) return;
      const moved = arrayMove(list, i, j);
      const next = { ...cur, [key]: moved };
      commitColumns(next);
      fireMove(id, key, moved);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [commitColumns],
  );

  const onQuickAdd = useCallback(
    (sectionKey: string, title: string) => {
      createTask.mutate({
        title,
        sectionId: sectionKey === NONE ? null : sectionKey,
      });
    },
    [createTask],
  );

  // ── Peek open / close / swap ──────────────────────────────────────────────
  const openPeek = useCallback(
    (id: string) => {
      setSelectedId(id);
      router.push(`${pathname}?task=${id}`);
    },
    [pathname, router],
  );
  const closePeek = useCallback(() => {
    router.push(pathname);
    if (selectedId) {
      requestAnimationFrame(() => {
        document
          .querySelector<HTMLElement>(`[data-row-id="${selectedId}"]`)
          ?.focus();
      });
    }
  }, [pathname, router, selectedId]);

  // Keep selection in sync when the peek is driven by the URL.
  useEffect(() => {
    if (openTaskId) setSelectedId(openTaskId);
  }, [openTaskId]);

  // Ordered list of currently-visible task ids for ↑/↓ navigation.
  const orderedVisible = useMemo(() => {
    const out: string[] = [];
    for (const k of model.groupKeys) {
      if (collapsed[k]) continue;
      for (const id of colFor(k)) out.push(id);
      if (completedExpanded[k])
        for (const t of model.completedByKey[k] ?? []) if (t.id) out.push(t.id);
    }
    return out;
  }, [model, collapsed, completedExpanded, colFor]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const el = e.target as HTMLElement | null;
      const tag = el?.tagName;
      if (
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        tag === "SELECT" ||
        el?.isContentEditable ||
        el?.closest('[role="menu"]')
      ) {
        return;
      }
      if (anyPopoverOpen()) return;

      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        if (orderedVisible.length === 0) return;
        e.preventDefault();
        const cur = selectedId ? orderedVisible.indexOf(selectedId) : -1;
        let idx = cur + (e.key === "ArrowDown" ? 1 : -1);
        if (cur === -1) idx = e.key === "ArrowDown" ? 0 : orderedVisible.length - 1;
        idx = Math.max(0, Math.min(orderedVisible.length - 1, idx));
        const next = orderedVisible[idx];
        setSelectedId(next);
        if (openTaskId) router.replace(`${pathname}?task=${next}`);
        requestAnimationFrame(() =>
          document
            .querySelector(`[data-row-id="${next}"]`)
            ?.scrollIntoView({ block: "nearest" }),
        );
      } else if (e.key === "Enter") {
        if (selectedId) {
          e.preventDefault();
          openPeek(selectedId);
        }
      } else if (e.key === "x" || e.key === "X") {
        if (selectedId) {
          e.preventDefault();
          document
            .querySelector<HTMLButtonElement>(`[data-task-complete="${selectedId}"]`)
            ?.click();
        }
      } else if (e.key === "Escape") {
        if (openTaskId) {
          // Drawer handles its own Esc-to-close.
        } else if (selectedId) {
          setSelectedId(null);
        }
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [orderedVisible, selectedId, openTaskId, pathname, router, openPeek]);

  const peekTask = openTaskId ? model.taskMap.get(openTaskId) : undefined;
  const [lastPeekTask, setLastPeekTask] = useState<Task | undefined>(undefined);
  useEffect(() => {
    if (peekTask) setLastPeekTask(peekTask);
  }, [peekTask]);
  const peekContent = peekTask ?? lastPeekTask;

  // Close the peek if its task vanished (deleted).
  useEffect(() => {
    if (openTaskId && !tasksQ.isLoading && !model.taskMap.has(openTaskId)) {
      router.replace(pathname);
    }
  }, [openTaskId, model, tasksQ.isLoading, pathname, router]);

  const loading = project.isLoading || sectionsQ.isLoading || tasksQ.isLoading;

  return (
    <div className={styles.wrap}>
      <div className={styles.header}>
        <div>
          <div className={styles.breadcrumb}>Projects</div>
          <h1 className={styles.title}>
            <span
              className={styles.projectDot}
              style={project.data?.color ? { background: project.data.color } : undefined}
              aria-hidden="true"
            />
            {project.data?.name ?? "Project"}
          </h1>
          <div className={styles.tabs} role="tablist" aria-label="Project views">
            <span className={`${styles.tab} ${styles.tabActive}`} role="tab" aria-selected="true">
              List
            </span>
            <span
              className={`${styles.tab} ${styles.tabDisabled}`}
              role="tab"
              aria-disabled="true"
              title="Board view — M2"
            >
              Board
            </span>
            <span
              className={`${styles.tab} ${styles.tabDisabled}`}
              role="tab"
              aria-disabled="true"
              title="Overview — M3"
            >
              Overview
            </span>
          </div>
        </div>
      </div>

      {loading ? (
        <div className={styles.scroll}>
          <div className={styles.loading}>
            <Skeleton height={20} />
            <Skeleton height={20} />
            <Skeleton height={20} width="60%" />
          </div>
        </div>
      ) : model.groupKeys.length === 0 ? (
        <div className={styles.scroll}>
          <EmptyState
            icon="✳"
            headline="No sections yet"
            body="Add tasks below to get started."
          />
        </div>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCorners}
          onDragStart={onDragStart}
          onDragOver={onDragOver}
          onDragEnd={onDragEnd}
        >
          <div className={styles.scroll}>
            <div className={styles.grid}>
              <div className={styles.colHead} aria-hidden="true">
                <span />
                <span className={styles.colName}>Name</span>
                <span>Assignee</span>
                <span>Due</span>
                <span>Priority</span>
                <span />
              </div>
              {model.groupKeys.map((k) => (
                <SectionGroup
                  key={k}
                  sectionKey={k}
                  name={model.groupNames[k] ?? "Section"}
                  incompleteIds={colFor(k)}
                  completedTasks={model.completedByKey[k] ?? []}
                  taskMap={model.taskMap}
                  sections={sections}
                  collapsed={!!collapsed[k]}
                  onToggleCollapse={() =>
                    setCollapsed((c) => ({ ...c, [k]: !c[k] }))
                  }
                  completedExpanded={!!completedExpanded[k]}
                  onToggleCompleted={() =>
                    setCompletedExpanded((c) => ({ ...c, [k]: !c[k] }))
                  }
                  selectedId={selectedId}
                  onOpenPeek={openPeek}
                  onSelect={setSelectedId}
                  onUpdate={onUpdate}
                  onComplete={onComplete}
                  onMoveToSection={onMoveToSection}
                  onMoveWithin={onMoveWithin}
                  onDelete={onDelete}
                  onQuickAdd={(title) => onQuickAdd(k, title)}
                />
              ))}
            </div>
          </div>
          <DragOverlay>
            {activeId ? (
              <div className={styles.overlay}>
                {model.taskMap.get(activeId)?.title ?? ""}
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}

      <Drawer open={!!openTaskId} onClose={closePeek} ariaLabel="Task detail">
        {peekContent && peekContent.id && (
          <TaskPeek
            task={peekContent}
            sections={sections}
            projectId={projectId}
            onClose={closePeek}
          />
        )}
      </Drawer>
    </div>
  );
}

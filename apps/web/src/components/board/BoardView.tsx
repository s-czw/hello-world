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
import { TaskPeek } from "@/components/task/TaskPeek";
import { useUsers, useProject, useSections, useTasks, useTaskMutations, type Task } from "@/lib/tasks";
import { BoardColumn } from "./BoardColumn";
import { BoardCard } from "./BoardCard";
import styles from "./board.module.css";

const NONE = "__none__";

interface Model {
  groupKeys: string[];
  groupNames: Record<string, string>;
  taskMap: Map<string, Task>;
  columns: Record<string, string[]>;
}

function buildModel(
  tasks: Task[],
  sectionIds: string[],
  sectionNames: Record<string, string>,
): Model {
  const known = new Set(sectionIds);
  const keyOf = (t: Task) =>
    t.sectionId && known.has(t.sectionId) ? t.sectionId : NONE;

  const taskMap = new Map<string, Task>();
  for (const t of tasks) if (t.id) taskMap.set(t.id, t);

  const groupKeys = [...sectionIds];
  const groupNames: Record<string, string> = { ...sectionNames };
  const hasNone = tasks.some((t) => keyOf(t) === NONE && !t.completed);
  if (hasNone || sectionIds.length === 0) {
    groupKeys.push(NONE);
    groupNames[NONE] = "No section";
  }

  const columns: Record<string, string[]> = {};
  for (const k of groupKeys) columns[k] = [];
  for (const t of tasks) {
    if (!t.id || t.completed) continue; // board shows active cards only
    const k = keyOf(t);
    if (!columns[k]) columns[k] = [];
    columns[k].push(t.id);
  }
  return { groupKeys, groupNames, taskMap, columns };
}

export function BoardView({ projectId }: { projectId: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const openTaskId = searchParams.get("task");

  const project = useProject(projectId);
  const sectionsQ = useSections(projectId);
  const tasksQ = useTasks(projectId);
  const usersQ = useUsers();
  const { moveTask, createTask, updateTask } = useTaskMutations(projectId);

  const sections = useMemo(() => sectionsQ.data ?? [], [sectionsQ.data]);
  const tasks = useMemo(() => tasksQ.data ?? [], [tasksQ.data]);

  const userName = useCallback(
    (id: string | undefined) => {
      if (!id) return undefined;
      const u = (usersQ.data ?? []).find((x) => x.id === id);
      return u?.name ?? u?.email;
    },
    [usersQ.data],
  );

  const model = useMemo(() => {
    const ids = sections.map((s) => s.id ?? "").filter(Boolean);
    const names: Record<string, string> = {};
    for (const s of sections) if (s.id) names[s.id] = s.name ?? "Untitled section";
    return buildModel(tasks, ids, names);
  }, [tasks, sections]);

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

  const colFor = useCallback(
    (k: string) => columns[k] ?? model.columns[k] ?? [],
    [columns, model],
  );

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
  );

  const keyOfId = (cols: Record<string, string[]>, id: string): string | null => {
    if (cols[id]) return id;
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

  const onQuickAdd = useCallback(
    (sectionKey: string, title: string) =>
      createTask.mutate({ title, sectionId: sectionKey === NONE ? null : sectionKey }),
    [createTask],
  );
  const onComplete = useCallback(
    (id: string, completed: boolean) => updateTask.mutate({ id, patch: { completed } }),
    [updateTask],
  );

  // ── Peek (reuse ?task= side-peek) ──────────────────────────────────────
  const openPeek = useCallback(
    (id: string) => router.push(`${pathname}?task=${id}`),
    [pathname, router],
  );
  const closePeek = useCallback(() => router.push(pathname), [pathname, router]);

  const peekTask = openTaskId ? model.taskMap.get(openTaskId) : undefined;
  const [lastPeekTask, setLastPeekTask] = useState<Task | undefined>(undefined);
  useEffect(() => {
    if (peekTask) setLastPeekTask(peekTask);
  }, [peekTask]);
  const peekContent = peekTask ?? lastPeekTask;

  useEffect(() => {
    if (openTaskId && !tasksQ.isLoading && !model.taskMap.has(openTaskId)) {
      router.replace(pathname);
    }
  }, [openTaskId, model, tasksQ.isLoading, pathname, router]);

  const loading = project.isLoading || sectionsQ.isLoading || tasksQ.isLoading;
  const activeTask = activeId ? model.taskMap.get(activeId) : undefined;

  if (loading) {
    return (
      <div className={styles.boardScroll}>
        <div className={styles.column}>
          <Skeleton height={28} />
          <Skeleton height={68} />
          <Skeleton height={68} />
        </div>
        <div className={styles.column}>
          <Skeleton height={28} />
          <Skeleton height={68} />
        </div>
      </div>
    );
  }

  if (model.groupKeys.length === 0) {
    return (
      <div className={styles.boardScroll}>
        <EmptyState
          icon="▦"
          headline="No sections yet"
          body="Add a section in the List view to start building your board."
        />
      </div>
    );
  }

  return (
    <>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={onDragStart}
        onDragOver={onDragOver}
        onDragEnd={onDragEnd}
      >
        <div className={styles.boardScroll}>
          {model.groupKeys.map((k) => (
            <BoardColumn
              key={k}
              sectionKey={k}
              name={model.groupNames[k] ?? "Section"}
              ids={colFor(k)}
              taskMap={model.taskMap}
              userName={userName}
              onOpenPeek={openPeek}
              onComplete={onComplete}
              onQuickAdd={(title) => onQuickAdd(k, title)}
            />
          ))}
        </div>
        <DragOverlay>
          {activeTask ? (
            <div className={styles.cardOverlay}>
              <BoardCard
                task={activeTask}
                userName={userName}
                onOpenPeek={() => {}}
                onComplete={() => {}}
                overlay
              />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>

      <Drawer open={!!openTaskId} onClose={closePeek} ariaLabel="Task detail">
        {peekContent && peekContent.id && (
          <TaskPeek
            task={peekContent}
            sections={sections}
            projectId={projectId}
            onClose={closePeek}
            onOpenTask={(id) => router.replace(`${pathname}?task=${id}`)}
          />
        )}
      </Drawer>
    </>
  );
}

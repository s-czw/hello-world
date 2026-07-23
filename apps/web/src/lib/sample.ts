"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { components } from "@cairn/api-client";
import { api } from "./api";

type Team = components["schemas"]["TeamResponse"];
type Section = components["schemas"]["SectionResponse"];
type Task = components["schemas"]["TaskResponse"];

/**
 * Builds a deletable "Sample project" client-side so list/board/peek demo well
 * in a fresh workspace (docs/03 §4.i seed content). The API bootstrap seeds only
 * the org + admin + a General team (no sample project), so this fills that gap;
 * it reuses the server-seeded default sections (To do / In progress / Done) and
 * lays down a handful of tasks, one subtask, and a status update. The project is
 * an ordinary project — the owner can delete it in one click.
 */
const SAMPLE_NAME = "Sample project";
const SAMPLE_COLOR = "#4F46E5";

const SEED_TASKS: Record<string, { title: string; priority?: string }[]> = {
  "To do": [
    { title: "Confirm venue booking", priority: "high" },
    { title: "Draft the kickoff agenda", priority: "medium" },
    { title: "Order signage", priority: "low" },
  ],
  "In progress": [
    { title: "Collect vendor quotes", priority: "medium" },
    { title: "Build the run-of-show", priority: "high" },
    { title: "Write the welcome email" },
  ],
  Done: [
    { title: "Set the event date" },
    { title: "Pick a project name" },
  ],
};

export function useCreateSampleProject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<Task> => {
      // 1. A team to hang the project off (General is seeded at bootstrap).
      const { data: teamsData, error: teamsErr } = await api.GET("/api/v1/teams");
      if (teamsErr) throw teamsErr;
      const team = ((teamsData?.data ?? []) as Team[])[0];
      if (!team?.id) throw new Error("No team available for the sample project.");

      // 2. Create the project (API seeds the 3 default sections).
      const { data: projData, error: projErr } = await api.POST(
        "/api/v1/projects",
        {
          body: {
            name: SAMPLE_NAME,
            teamId: team.id,
            color: SAMPLE_COLOR,
            defaultView: "list",
            description:
              "A ready-made example so you can explore lists, the board, and the task side-peek. Delete it whenever you like.",
          },
        },
      );
      if (projErr || !projData?.data?.id) throw projErr ?? new Error("create failed");
      const project = projData.data as Task;
      const projectId = project.id as string;

      // 3. Resolve the seeded sections by name.
      const { data: secData, error: secErr } = await api.GET(
        "/api/v1/projects/{projectId}/sections",
        { params: { path: { projectId } } },
      );
      if (secErr) throw secErr;
      const sections = (secData?.data ?? []) as Section[];
      const byName = new Map(sections.map((s) => [s.name ?? "", s.id ?? ""]));

      // 4. Seed tasks into each section (sequential — small, keeps ordering sane).
      let firstTodoTaskId: string | null = null;
      for (const [sectionName, tasks] of Object.entries(SEED_TASKS)) {
        const sectionId = byName.get(sectionName);
        if (!sectionId) continue;
        for (const t of tasks) {
          const { data: taskData, error: taskErr } = await api.POST(
            "/api/v1/projects/{projectId}/tasks",
            {
              params: { path: { projectId } },
              body: { title: t.title, sectionId, priority: t.priority },
            },
          );
          if (taskErr) throw taskErr;
          const created = taskData?.data as Task | undefined;
          if (sectionName === "To do" && !firstTodoTaskId && created?.id) {
            firstTodoTaskId = created.id;
          }
        }
      }

      // 5. One subtask on the first To-do task (shows the checklist in the peek).
      if (firstTodoTaskId) {
        await api.POST("/api/v1/tasks/{id}/subtasks", {
          params: { path: { id: firstTodoTaskId } },
          body: { title: "Get the deposit invoice" },
        });
      }

      // 6. A status update so the Overview + roll-up demo well.
      await api.POST("/api/v1/projects/{projectId}/status-updates", {
        params: { path: { projectId } },
        body: {
          status: "on_track",
          title: "Kickoff on schedule",
          body: "Venue and date are locked; vendor quotes are coming in this week.",
        },
      });

      return project;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["projects"] });
    },
  });
}

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, problemMessage } from "@/lib/api";
import type { components } from "@cairn/api-client";
import { useToast } from "@/components/ui/Toast";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import styles from "../../page.module.css";

type Team = components["schemas"]["TeamResponse"];
type Project = components["schemas"]["ProjectResponse"];

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

export default function NewProjectPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();

  const teams = useQuery({
    queryKey: ["teams"],
    queryFn: async () => {
      const { data, error } = await api.GET("/api/v1/teams");
      if (error) throw error;
      return (data?.data ?? []) as Team[];
    },
  });

  const [name, setName] = useState("");
  const [teamId, setTeamId] = useState("");
  const [color, setColor] = useState(COLORS[0]);
  const [description, setDescription] = useState("");
  // Optional schedule window (spec B2). Drives the portfolio schedule view — a project created
  // without dates lands in the "not scheduled" tray until they are filled in.
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!teamId && teams.data && teams.data.length > 0) {
      setTeamId(teams.data[0].id ?? "");
    }
  }, [teams.data, teamId]);

  function close() {
    router.back();
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim() || !teamId) {
      setError("A name and team are required.");
      return;
    }
    if (startDate && endDate && endDate < startDate) {
      setError("The end date cannot be before the start date.");
      return;
    }
    setSubmitting(true);
    const { data, error: err } = await api.POST("/api/v1/projects", {
      body: {
        name: name.trim(),
        teamId,
        color,
        description: description.trim() || undefined,
        defaultView: "list",
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      },
    });
    if (err || !data?.data?.id) {
      setSubmitting(false);
      setError(problemMessage(err, "Couldn't create the project."));
      return;
    }
    const project = data.data as Project;
    // The API seeds the three default sections (To do / In progress / Done)
    // server-side on create (spec B2) — no client seeding, else we'd get six.
    await qc.invalidateQueries({ queryKey: ["projects"] });
    toast.success("Project created");
    router.push(`/projects/${project.id}/list`);
  }

  return (
    <Modal open onClose={close} title="New project">
      <form className={styles.newProjectForm} onSubmit={onSubmit}>
        {error && (
          <div className={styles.formError} role="alert">
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
          placeholder="e.g. Website relaunch"
        />
        <Select
          label="Team"
          value={teamId}
          onChange={(e) => setTeamId(e.target.value)}
          options={(teams.data ?? []).map((t) => ({
            value: t.id ?? "",
            label: t.name ?? "Team",
          }))}
        />
        <div className={styles.field}>
          <span className={styles.fieldLabel}>Color</span>
          <div className={styles.swatchRow}>
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                className={styles.swatch}
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
        <div className={styles.newProjectDates}>
          <Input
            label="Start date (optional)"
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            modal
          />
          <Input
            label="End date (optional)"
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            modal
          />
        </div>
        <div className={styles.newProjectFooter}>
          <Button variant="secondary" size="dialog" onClick={close} type="button">
            Cancel
          </Button>
          <Button size="dialog" type="submit" loading={submitting}>
            Create project
          </Button>
        </div>
      </form>
    </Modal>
  );
}

"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { api, problemMessage } from "@/lib/api";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import styles from "../auth.module.css";

export default function SetupPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [orgName, setOrgName] = useState("");
  const [adminName, setAdminName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const { error: err, response } = await api.POST("/api/v1/auth/bootstrap", {
      body: { orgName, adminName, email, password },
    });
    setSubmitting(false);
    if (err || !response.ok) {
      setError(problemMessage(err, "Could not create workspace."));
      return;
    }
    await qc.invalidateQueries();
    router.push("/my-tasks");
    router.refresh();
  }

  return (
    <div className={styles.screen}>
      <div className={styles.card}>
        <div className={styles.brandRow}>
          <span className={styles.brandMark} aria-hidden="true" />
          <span className={styles.brandName}>Cairn</span>
        </div>
        <h1 className={styles.title}>Set up your workspace</h1>
        <p className={styles.subtitle}>
          Create your organization and the first admin account.
        </p>
        <form className={styles.form} onSubmit={onSubmit}>
          {error && (
            <div className={styles.formError} role="alert">
              {error}
            </div>
          )}
          <Input
            label="Organization name"
            value={orgName}
            onChange={(e) => setOrgName(e.target.value)}
            required
            autoFocus
            autoComplete="organization"
          />
          <Input
            label="Your name"
            value={adminName}
            onChange={(e) => setAdminName(e.target.value)}
            required
            autoComplete="name"
          />
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            hint="At least 8 characters."
            autoComplete="new-password"
          />
          <Button type="submit" size="dialog" block loading={submitting}>
            Create workspace
          </Button>
        </form>
      </div>
    </div>
  );
}

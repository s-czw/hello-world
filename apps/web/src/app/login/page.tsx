"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { api, problemMessage } from "@/lib/api";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import styles from "../auth.module.css";

export default function LoginPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const { error: err, response } = await api.POST("/api/v1/auth/login", {
      body: { email, password },
    });
    setSubmitting(false);
    if (err || !response.ok) {
      setError(problemMessage(err, "Invalid email or password."));
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
        <h1 className={styles.title}>Sign in</h1>
        <p className={styles.subtitle}>Welcome back.</p>
        <form className={styles.form} onSubmit={onSubmit}>
          {error && (
            <div className={styles.formError} role="alert">
              {error}
            </div>
          )}
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
            autoComplete="email"
          />
          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
          <Button type="submit" size="dialog" block loading={submitting}>
            Sign in
          </Button>
        </form>
      </div>
    </div>
  );
}

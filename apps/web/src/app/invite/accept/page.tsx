"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, problemMessage } from "@/lib/api";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import styles from "../../auth.module.css";

function AcceptForm() {
  const router = useRouter();
  const params = useSearchParams();
  const token = params.get("token") ?? "";
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <div className={styles.card}>
        <h1 className={styles.title}>Invalid invite</h1>
        <p className={styles.subtitle}>
          This invite link is missing its token. Ask your admin for a fresh link.
        </p>
      </div>
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const { error: err, response } = await api.POST(
      "/api/v1/invites/accept",
      { body: { token, name, password } },
    );
    setSubmitting(false);
    if (err || !response.ok) {
      setError(problemMessage(err, "Could not accept this invite."));
      return;
    }
    setDone(true);
    setTimeout(() => router.push("/login"), 1200);
  }

  if (done) {
    return (
      <div className={styles.card}>
        <h1 className={styles.title}>You&apos;re in</h1>
        <p className={styles.subtitle}>
          Your account is ready. Redirecting you to sign in…
        </p>
      </div>
    );
  }

  return (
    <div className={styles.card}>
      <div className={styles.brandRow}>
        <span className={styles.brandMark} aria-hidden="true" />
        <span className={styles.brandName}>Cairn</span>
      </div>
      <h1 className={styles.title}>Accept your invite</h1>
      <p className={styles.subtitle}>Set your name and a password to join.</p>
      <form className={styles.form} onSubmit={onSubmit}>
        {error && (
          <div className={styles.formError} role="alert">
            {error}
          </div>
        )}
        <Input
          label="Your name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          autoFocus
          autoComplete="name"
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
          Join workspace
        </Button>
      </form>
    </div>
  );
}

export default function AcceptInvitePage() {
  return (
    <div className={styles.screen}>
      <Suspense
        fallback={
          <div className={styles.card}>
            <p className={styles.subtitle}>Loading…</p>
          </div>
        }
      >
        <AcceptForm />
      </Suspense>
    </div>
  );
}

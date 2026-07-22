"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useMe, isAdmin } from "@/lib/auth";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import styles from "./shell.module.css";

export function Topbar() {
  const { data: me } = useMe();
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const qc = useQueryClient();
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function signOut() {
    await api.POST("/api/v1/auth/logout");
    qc.clear();
    router.push("/login");
    router.refresh();
  }

  return (
    <header className={styles.topbar}>
      <div />
      <div className={styles.topbarRight}>
        <Button size="compact" onClick={() => router.push("/projects/new")}>
          + New
        </Button>
        <div className={styles.userMenuWrap} ref={wrapRef}>
          <button
            type="button"
            className={styles.userTrigger}
            aria-haspopup="menu"
            aria-expanded={open}
            aria-label="User menu"
            onClick={() => setOpen((o) => !o)}
          >
            <Avatar name={me?.name ?? "?"} seed={me?.id} size={24} />
          </button>
          {open && (
            <div className={styles.menu} role="menu">
              <div className={styles.menuHeader}>
                <div className={styles.menuName}>{me?.name}</div>
                <div className={styles.menuEmail}>{me?.email}</div>
              </div>
              {isAdmin(me) && (
                <Link
                  href="/settings/members"
                  className={styles.menuItem}
                  role="menuitem"
                  onClick={() => setOpen(false)}
                >
                  Members
                </Link>
              )}
              <button
                type="button"
                className={styles.menuItem}
                role="menuitem"
                onClick={signOut}
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

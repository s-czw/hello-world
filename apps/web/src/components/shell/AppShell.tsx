"use client";

import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import styles from "./shell.module.css";

/** Two-region app frame: 240px sidebar + 48px topbar (docs/03 §3.1). */
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.frame}>
      <Sidebar />
      <div className={styles.main}>
        <Topbar />
        <main className={styles.content}>{children}</main>
      </div>
    </div>
  );
}

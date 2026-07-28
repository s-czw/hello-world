import AxeBuilder from "@axe-core/playwright";
import { expect, test, type APIRequestContext } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { ADMIN } from "./support/accounts";

/**
 * Automated accessibility scan (DoD item 4 / M4 CONTRACT item C).
 *
 * Seeds a realistic workspace through the API — two members, three projects with
 * sections/tasks/subtasks/comments/an attachment/status updates, a portfolio and a
 * couple of notifications — then runs axe-core against every authenticated surface
 * (including the task side-peek open). Empty screens hide violations, so every
 * surface is scanned with real content in it.
 *
 * Gate: zero `serious` and zero `critical` violations across all surfaces.
 * `moderate`/`minor` findings are logged (and attached to the HTML report) but do
 * not fail the run.
 *
 * Runs against the same already-booted API + web servers as the smoke
 * (scripts/run-e2e.mjs); the `a11y` Playwright project depends on `smoke`, so on a
 * shared run the org already exists and this spec logs in instead of bootstrapping.
 */

/** WCAG 2.2 AA is the target (docs/03 §6); best-practice rules are scanned too and logged. */
const TAGS = [
  "wcag2a",
  "wcag2aa",
  "wcag21a",
  "wcag21aa",
  "wcag22aa",
  "best-practice",
];

const BLOCKING: ReadonlyArray<string | undefined> = ["serious", "critical"];

type Finding = {
  surface: string;
  id: string;
  impact: string;
  help: string;
  helpUrl: string;
  nodes: Array<{ target: string; why: string }>;
};

const targets = (f: Finding) => f.nodes.map((n) => n.target).join(" | ");

function iso(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().slice(0, 10);
}

// ── tiny typed API helper (envelope is { data, meta }; writes need the CSRF header) ──

async function csrfHeader(ctx: APIRequestContext): Promise<Record<string, string>> {
  const state = await ctx.storageState();
  const token = state.cookies.find((c) => c.name === "cairn_csrf")?.value;
  return token ? { "X-CSRF-Token": token } : {};
}

async function post<T>(ctx: APIRequestContext, url: string, data: unknown = {}): Promise<T> {
  const res = await ctx.post(url, { data, headers: await csrfHeader(ctx) });
  if (!res.ok()) throw new Error(`POST ${url} → ${res.status()} ${await res.text()}`);
  const text = await res.text();
  return (text ? JSON.parse(text).data : undefined) as T;
}

async function patch<T>(ctx: APIRequestContext, url: string, data: unknown): Promise<T> {
  const res = await ctx.patch(url, { data, headers: await csrfHeader(ctx) });
  if (!res.ok()) throw new Error(`PATCH ${url} → ${res.status()} ${await res.text()}`);
  const text = await res.text();
  return (text ? JSON.parse(text).data : undefined) as T;
}

async function get<T>(ctx: APIRequestContext, url: string): Promise<T> {
  const res = await ctx.get(url);
  if (!res.ok()) throw new Error(`GET ${url} → ${res.status()} ${await res.text()}`);
  return (await res.json()).data as T;
}

type Id = { id: string };
type Named = Id & { name: string };

test("a11y: axe scan of the authenticated surfaces", async ({
  page,
  request,
  playwright,
  baseURL,
}) => {
  test.setTimeout(300_000);

  const uniq = Date.now();
  const consoleErrors: string[] = [];
  page.on("console", (m) => {
    if (m.type() === "error") consoleErrors.push(m.text());
  });
  page.on("pageerror", (e) => consoleErrors.push(String(e.message)));

  // ── 1. Session: bootstrap the org when this spec runs alone, else log in ──────
  const seed: {
    projectId: string;
    taskId: string;
    portfolioId: string;
  } = { projectId: "", taskId: "", portfolioId: "" };

  await test.step("seed a realistic workspace through the API", async () => {
    const { needsBootstrap } = await get<{ needsBootstrap: boolean }>(
      request,
      "/api/v1/auth/bootstrap-status",
    );
    if (needsBootstrap) {
      await post(request, "/api/v1/auth/bootstrap", {
        orgName: ADMIN.org,
        adminName: ADMIN.name,
        email: ADMIN.email,
        password: ADMIN.password,
      });
    } else {
      await post(request, "/api/v1/auth/login", {
        email: ADMIN.email,
        password: ADMIN.password,
      });
    }
    const me = await get<{ id: string }>(request, "/api/v1/auth/me");
    const adaId = me.id;

    // A second member so avatars/assignee pickers/member tables have real rows.
    const beaEmail = `bea${uniq}@cairn.test`;
    const invite = await post<{ acceptUrl: string }>(request, "/api/v1/invites", {
      email: beaEmail,
    });
    const token = new URL(invite.acceptUrl, "http://placeholder").searchParams.get("token");
    await post(request, "/api/v1/invites/accept", {
      token,
      name: "Bea Member",
      password: ADMIN.password,
    });
    const users = await get<Array<{ id: string; email: string }>>(request, "/api/v1/users");
    const beaId = users.find((u) => u.email === beaEmail)!.id;

    // A still-pending invite so /settings/members shows the invites table too.
    await post(request, "/api/v1/invites", { email: `pending${uniq}@cairn.test` });

    const teams = await get<Named[]>(request, "/api/v1/teams");
    const teamId = teams[0].id;

    // Three projects: two scheduled (schedule bars) + one undated (the "no dates" tray).
    const audit = await post<Named>(request, "/api/v1/projects", {
      name: `Accessibility Audit ${uniq}`,
      description: "WCAG 2.2 AA pass over every Cairn surface. Notes: https://example.com/a11y",
      color: "#4f46e5",
      teamId,
      startDate: iso(-14),
      endDate: iso(21),
    });
    seed.projectId = audit.id;
    const design = await post<Named>(request, "/api/v1/projects", {
      name: `Design System ${uniq}`,
      description: "Token and component clean-up.",
      color: "#0ea5e9",
      teamId,
      startDate: iso(-3),
      endDate: iso(40),
    });
    const backlog = await post<Named>(request, "/api/v1/projects", {
      name: `Backlog Grooming ${uniq}`,
      color: "#f59e0b",
      teamId,
    });

    const sections = await get<Named[]>(request, `/api/v1/projects/${audit.id}/sections`);
    const [todo, inProgress, done] = sections;

    const mkTask = (body: Record<string, unknown>) =>
      post<Id>(request, `/api/v1/projects/${audit.id}/tasks`, body);

    const contrast = await mkTask({
      title: "Audit colour contrast",
      sectionId: todo.id,
      assigneeId: adaId,
      dueDate: iso(0),
      priority: "high",
    });
    seed.taskId = contrast.id;
    await mkTask({
      title: "Fix focus order in the side-peek",
      sectionId: todo.id,
      assigneeId: beaId,
      dueDate: iso(-4),
      priority: "medium",
    });
    await mkTask({
      title: "Label every icon-only button",
      sectionId: inProgress.id,
      assigneeId: adaId,
      dueDate: iso(3),
      priority: "low",
    });
    await mkTask({ title: "Write the a11y notes", sectionId: inProgress.id });
    const shipped = await mkTask({
      title: "Ship the audit report",
      sectionId: done.id,
      assigneeId: adaId,
      dueDate: iso(-1),
      priority: "high",
    });
    await patch(request, `/api/v1/tasks/${shipped.id}`, { completed: true });

    // Subtasks (one complete → the peek shows 1/2 progress).
    const sub1 = await post<Id>(request, `/api/v1/tasks/${contrast.id}/subtasks`, {
      title: "Check the status chips",
    });
    await post<Id>(request, `/api/v1/tasks/${contrast.id}/subtasks`, {
      title: "Check the priority flags",
    });
    await patch(request, `/api/v1/tasks/${sub1.id}`, { completed: true, assigneeId: beaId });

    // A comment + an attachment so the activity stream and chips render.
    await post(request, `/api/v1/tasks/${contrast.id}/comments`, {
      body: `Starting the sweep — @${beaEmail} can you take the board? Ref https://example.com/wcag`,
    });
    const upload = await request.post(`/api/v1/tasks/${contrast.id}/attachments`, {
      headers: await csrfHeader(request),
      multipart: {
        file: {
          name: "note.txt",
          mimeType: "text/plain",
          buffer: Buffer.from("Cairn a11y scan attachment"),
        },
      },
    });
    expect(upload.ok(), `attachment upload → ${upload.status()}`).toBeTruthy();

    // Status updates → real chips on the overview, roll-up and schedule.
    await post(request, `/api/v1/projects/${audit.id}/status-updates`, {
      status: "at_risk",
      title: "Status update — audit",
      body: "Contrast tokens need another pass before the pilot.",
    });
    await post(request, `/api/v1/projects/${design.id}/status-updates`, {
      status: "on_track",
      title: "Status update — design system",
      body: "Tokens landed.",
    });

    // Portfolio with all three projects (roll-up + schedule content).
    const portfolio = await post<Named>(request, "/api/v1/portfolios", {
      name: `Delivery Portfolio ${uniq}`,
      description: "Everything shipping this quarter.",
      color: "#4f46e5",
    });
    seed.portfolioId = portfolio.id;
    for (const p of [audit, design, backlog]) {
      await post(request, `/api/v1/portfolios/${portfolio.id}/projects`, { projectId: p.id });
    }

    // Notifications for Ada: Bea assigns her a task and @mentions her in a comment.
    const beaCtx = await playwright.request.newContext({ baseURL });
    try {
      await post(beaCtx, "/api/v1/auth/login", { email: beaEmail, password: ADMIN.password });
      const beaTask = await post<Id>(request, `/api/v1/projects/${audit.id}/tasks`, {
        title: "Review the keyboard map",
        sectionId: todo.id,
      });
      await patch(beaCtx, `/api/v1/tasks/${beaTask.id}`, { assigneeId: adaId });
      await post(beaCtx, `/api/v1/tasks/${contrast.id}/comments`, {
        body: `On it @${ADMIN.email} — starting with the board columns.`,
      });
    } finally {
      await beaCtx.dispose();
    }
  });

  // ── 2. Log in through the UI so the browser holds a real session ─────────────
  await test.step("log in", async () => {
    await page.goto("/login");
    await page.getByLabel("Email").fill(ADMIN.email);
    await page.getByLabel("Password").fill(ADMIN.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.waitForURL("**/my-tasks", { timeout: 20_000 });
  });

  // ── 3. Scan every surface ────────────────────────────────────────────────────
  const findings: Finding[] = [];
  const evaluated = new Set<string>();
  const scanned: Array<{
    surface: string;
    rulesPassed: number;
    incomplete: number;
    incompleteIds: string[];
    // axe could not decide these automatically (e.g. text over a gradient/overlay);
    // kept in the JSON report so a human can eyeball them.
    incompleteNodes: Array<{ id: string; target: string; why: string }>;
  }> = [];

  async function scan(surface: string, ready: () => Promise<void>, url?: string) {
    await test.step(`scan ${surface}`, async () => {
      if (url) await page.goto(url);
      await ready();
      const results = await new AxeBuilder({ page }).withTags(TAGS).analyze();
      // Proof the scan really evaluated a populated page rather than an empty shell.
      expect(results.passes.length, `${surface}: axe evaluated rules`).toBeGreaterThan(10);
      for (const r of [...results.passes, ...results.violations, ...results.incomplete]) {
        evaluated.add(r.id);
      }
      scanned.push({
        surface,
        rulesPassed: results.passes.length,
        incomplete: results.incomplete.length,
        incompleteIds: results.incomplete.map((i) => i.id),
        incompleteNodes: results.incomplete.flatMap((i) =>
          i.nodes.slice(0, 4).map((n) => ({
            id: i.id,
            target: n.target.join(" "),
            why: (n.any?.[0]?.message ?? n.failureSummary ?? "").replace(/\s+/g, " ").trim(),
          })),
        ),
      });
      for (const v of results.violations) {
        findings.push({
          surface,
          id: v.id,
          impact: v.impact ?? "unknown",
          help: v.help,
          helpUrl: v.helpUrl,
          nodes: v.nodes.slice(0, 6).map((n) => ({
            target: n.target.join(" "),
            why: (n.failureSummary ?? "").replace(/\s+/g, " ").trim(),
          })),
        });
      }
    });
  }

  const p = seed.projectId;
  const f = seed.portfolioId;

  await scan("/my-tasks", async () => {
    await page.getByRole("heading", { name: "My Tasks" }).waitFor();
    await page.getByText("Audit colour contrast").first().waitFor();
  }, "/my-tasks");

  await scan("/projects/[id]/list", async () => {
    await page.getByRole("button", { name: /To do/ }).waitFor();
    await page.getByRole("button", { name: "Audit colour contrast" }).first().waitFor();
  }, `/projects/${p}/list`);

  await scan("/projects/[id]/list?task= (side-peek)", async () => {
    const peek = page.getByRole("dialog", { name: "Task detail" });
    await peek.waitFor();
    await peek.getByText("Check the status chips").waitFor();
    await peek.getByText("note.txt", { exact: true }).waitFor();
  }, `/projects/${p}/list?task=${seed.taskId}`);

  await scan("/projects/[id]/board", async () => {
    await page.getByRole("region", { name: "To do" }).waitFor();
    await page.getByRole("button", { name: "Open Audit colour contrast" }).waitFor();
  }, `/projects/${p}/board`);

  await scan("/projects/[id]/overview", async () => {
    await page.getByRole("heading", { name: "Status" }).waitFor();
    await page.getByText("Contrast tokens need another pass").first().waitFor();
  }, `/projects/${p}/overview`);

  await scan("/portfolios", async () => {
    await page.getByRole("heading", { name: "Portfolios" }).waitFor();
    await page.getByRole("link", { name: /Delivery Portfolio/ }).first().waitFor();
  }, "/portfolios");

  await scan("/portfolios/[id] (roll-up)", async () => {
    await expect(page.locator("tbody tr")).toHaveCount(3);
  }, `/portfolios/${f}`);

  await scan("/portfolios/[id]/schedule", async () => {
    await page.getByRole("link", { name: "Schedule" }).waitFor();
    await page.getByText(/Backlog Grooming/).first().waitFor();
  }, `/portfolios/${f}/schedule`);

  await scan("/notifications", async () => {
    await page.getByRole("heading", { name: "Notifications" }).waitFor();
    await page.getByText(/mentioned you in a comment/).first().waitFor();
  }, "/notifications");

  await scan("/settings/members", async () => {
    await page.getByRole("heading", { name: "Members" }).waitFor();
    await page.getByText("Bea Member").first().waitFor();
  }, "/settings/members");

  // ── 4. Report + gate ─────────────────────────────────────────────────────────
  expect(scanned.length, "every listed surface was scanned").toBe(10);
  // The rules the M4 contract calls out by name must actually have been applicable
  // somewhere — otherwise a green run would only mean "axe found nothing to check".
  for (const rule of ["color-contrast", "target-size", "label", "link-name", "region"]) {
    expect(evaluated.has(rule), `axe rule ${rule} was evaluated`).toBe(true);
  }

  const byImpact = (impact: string) => findings.filter((v) => v.impact === impact);
  const summary = {
    surfaces: scanned.length,
    total: findings.length,
    critical: byImpact("critical").length,
    serious: byImpact("serious").length,
    moderate: byImpact("moderate").length,
    minor: byImpact("minor").length,
  };

  const lines = [
    `axe (${TAGS.join(",")}) over ${summary.surfaces} surfaces — ` +
      `${summary.total} violations: critical=${summary.critical} serious=${summary.serious} ` +
      `moderate=${summary.moderate} minor=${summary.minor}`,
    ...scanned.map(
      (s) =>
        `  scanned ${s.surface} (${s.rulesPassed} rules passed, ${s.incomplete} incomplete` +
        `${s.incompleteIds.length ? ": " + s.incompleteIds.join(",") : ""})`,
    ),
    ...findings.map((v) => `  [${v.impact}] ${v.surface} — ${v.id}: ${v.help} (${targets(v)})`),
  ];
  console.log(lines.join("\n"));

  const report = JSON.stringify({ summary, scanned, findings }, null, 2);
  const reportPath = path.join(test.info().project.outputDir, "a11y-report.json");
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, report);
  await test.info().attach("axe-violations.json", {
    body: report,
    contentType: "application/json",
  });

  if (consoleErrors.length) console.log("console errors:\n  " + consoleErrors.join("\n  "));

  const blocking = findings.filter((v) => BLOCKING.includes(v.impact));
  expect(
    blocking.map((v) => `[${v.impact}] ${v.surface} — ${v.id} (${targets(v)})`),
    "zero serious/critical axe violations on the authenticated surfaces",
  ).toEqual([]);
});

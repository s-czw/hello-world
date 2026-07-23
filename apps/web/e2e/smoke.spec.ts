import { test, expect, type Page } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const FIXTURES = path.join(path.dirname(fileURLToPath(import.meta.url)), "fixtures");

/**
 * Cairn M1 + M2 + M3 end-to-end smoke.
 *
 * Runs against a freshly-reset `cairn` DB with the API + web servers already up
 * (see scripts/run-e2e.mjs). Exercises the full first-run happy path:
 *   /setup bootstrap → create team+project (+3 default sections) → quick-add 3
 *   tasks → keyboard nav → inline-edit due + priority → side-peek edits
 *   (assignee/due/description) → drag + ⋯-menu reorder → complete a task (motion)
 *   → My Tasks shows the correct due-date groups → open a task from My Tasks back
 *   into the project side-peek.
 *
 * Then the M2 layer, on top of the same project:
 *   create a portfolio → add the project → post a status update via the composer
 *   (opened from the roll-up chip) → the roll-up chip reflects the posted status
 *   → open the project board → drag a card between columns.
 *
 * Then the M3 layer, on top of the same project:
 *   invite + accept a second member (Bob) via the API → open a task peek → add a
 *   subtask and complete it (progress 0/1 → 1/1) → post a comment that @mentions
 *   Bob → upload a small .txt attachment and re-download it through the authz'd
 *   URL (200 + Content-Disposition: attachment + X-Content-Type-Options: nosniff
 *   + byte match) → log in as Bob in a fresh context and confirm the bell badge +
 *   the notifications inbox show the @mention.
 *
 * Any console.error / pageerror other than the known-benign Next.js RSC-prefetch
 * abort message fails the test.
 */

const BENIGN = [/Failed to fetch RSC payload/, /Falling back to browser navigation/];

/** Attach the console.error / pageerror guard used by the whole smoke. */
function attachErrorGuard(p: Page, errors: string[]): void {
  p.on("console", (m) => {
    if (m.type() !== "error") return;
    const t = m.text();
    if (BENIGN.some((re) => re.test(t))) return;
    errors.push("console.error: " + t);
  });
  p.on("pageerror", (e) => errors.push("pageerror: " + e.message));
}

function isoOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

test("M1 happy path: bootstrap → project → tasks → my-tasks", async ({ page, browser }) => {
  const errors: string[] = [];
  attachErrorGuard(page, errors);

  const uniq = Date.now();
  let projectId = "";
  const row = (title: string) =>
    page.locator("[data-row-id]").filter({ hasText: title }).first();

  // 1) Bootstrap (first-run)
  await test.step("bootstrap", async () => {
    await page.goto("/setup");
    await page.getByLabel("Organization name").fill("Acme Ops");
    await page.getByLabel("Your name").fill("Ada Admin");
    await page.getByLabel("Email").fill(`ada${uniq}@example.com`);
    await page.getByLabel("Password").fill("password123");
    await page.getByRole("button", { name: "Create workspace" }).click();
    await page.waitForURL("**/my-tasks", { timeout: 15000 });
  });

  // 2) Create a project (+ default sections)
  await test.step("create project", async () => {
    await page.goto("/projects/new");
    await page.getByLabel("Name").fill("Launch");
    await page.getByRole("button", { name: "Create project" }).click();
    await page.waitForURL("**/projects/**/list", { timeout: 15000 });
    projectId = page.url().match(/projects\/([0-9a-f-]{36})/)?.[1] ?? "";
    expect(projectId, "captured the new project id").not.toEqual("");
    await page.getByRole("button", { name: /To do/ }).waitFor();
    await page.getByRole("button", { name: /In progress/ }).waitFor();
  });

  // 3) Quick-add 3 tasks in the first section
  const titles = ["Design homepage", "Write copy", "Set up analytics"];
  await test.step("quick-add 3 tasks", async () => {
    const quick = page.getByLabel("Add a task").first();
    for (const t of titles) {
      await quick.click();
      await quick.fill(t);
      await quick.press("Enter");
      await page.getByRole("button", { name: t }).first().waitFor();
    }
    await expect(page.locator("[data-row-id]")).toHaveCount(3);
  });

  // 3b) Keyboard: ↓ selects, Enter opens peek, Esc closes
  await test.step("keyboard nav (↑/↓, Enter, Esc)", async () => {
    await page.getByLabel("Add a task").first().press("Escape");
    await page.locator("body").click({ position: { x: 5, y: 5 } });
    await page.keyboard.press("ArrowDown");
    await page.keyboard.press("Enter");
    const kbPeek = page.getByRole("dialog", { name: "Task detail" });
    await kbPeek.waitFor();
    await expect(kbPeek.getByLabel("Task title")).toHaveValue("Design homepage");
    await page.keyboard.press("Escape");
    await kbPeek.waitFor({ state: "hidden" });
  });

  // 4) Inline edit due + priority on 'Design homepage'
  await test.step("inline edit due + priority", async () => {
    const r1 = row("Design homepage");
    await r1.getByRole("button", { name: "Due date" }).click();
    await page.getByLabel("Type a date").fill("aug 12");
    await page.getByLabel("Type a date").press("Enter");
    await r1.getByRole("button", { name: /^Priority:/ }).click();
    await page.getByRole("option", { name: "High" }).click();
    await r1.getByRole("button", { name: "Priority: High" }).waitFor();
  });

  // 5) Assign + due via the side-peek so My Tasks groups populate
  async function peekEdit(
    title: string,
    dateAction: () => Promise<void>,
    addDesc?: boolean,
  ) {
    await page.getByRole("button", { name: title }).first().click();
    const peek = page.getByRole("dialog", { name: "Task detail" });
    await peek.waitFor();
    await peek.getByRole("button", { name: "Assign task" }).click();
    await page.getByLabel("Search people").fill("Ada");
    await page.getByRole("option", { name: /Ada Admin/ }).click();
    await peek.getByRole("button", { name: "Due date" }).click();
    await dateAction();
    if (addDesc) {
      await peek.getByText("Add a description…").click();
      await peek
        .getByLabel("Description")
        .fill("See the brief at https://example.com/brief");
      await peek.getByLabel("Task title").click(); // blur description to commit
    }
    await peek.getByRole("button", { name: "Assignee: Ada Admin" }).waitFor();
    await page.keyboard.press("Escape");
    await peek.waitFor({ state: "hidden" });
  }

  await test.step("side-peek edits (assignee, due, description)", async () => {
    await peekEdit(
      "Design homepage",
      async () => {
        await page.getByRole("button", { name: "Today" }).click();
      },
      true,
    );
    await peekEdit("Write copy", async () => {
      await page.getByLabel("Type a date").fill("2020-01-01");
      await page.getByLabel("Type a date").press("Enter");
    });
    await peekEdit("Set up analytics", async () => {
      await page.getByLabel("Type a date").fill(isoOffset(3));
      await page.getByLabel("Type a date").press("Enter");
    });
  });

  // 6) Drag reorder (pointer) then a deterministic cross-section move (⋯ menu)
  await test.step("drag reorder + cross-section move", async () => {
    async function dragWithin(fromTitle: string) {
      const handle = row(fromTitle).locator('button[aria-label="Drag to reorder"]');
      await row(fromTitle).hover();
      const hb = await handle.boundingBox();
      if (!hb) throw new Error("no handle box for " + fromTitle);
      await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2);
      await page.mouse.down();
      await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2 + 8, { steps: 3 });
      await page.mouse.move(hb.x + hb.width / 2, hb.y + hb.height / 2 + 60, { steps: 12 });
      await page.mouse.up();
      await page.waitForTimeout(300);
    }
    await dragWithin("Write copy");

    await row("Set up analytics").hover();
    await row("Set up analytics").getByRole("button", { name: "Task actions" }).click();
    await page.getByRole("menuitem", { name: "In progress" }).click();
    await expect(page.getByRole("button", { name: "Set up analytics" }).first()).toBeVisible();
  });

  // 7) Complete a task (motion) → moves to Completed group
  await test.step("complete a task (motion)", async () => {
    await row("Design homepage").hover();
    await row("Design homepage").getByRole("button", { name: "Mark complete" }).click();
    await page
      .getByRole("button", { name: /^Completed \(/ })
      .first()
      .waitFor({ timeout: 5000 });
  });

  // 8) My Tasks groups + sidebar badge
  await test.step("my-tasks groups", async () => {
    await page.getByRole("link", { name: /My Tasks/ }).click();
    await page.waitForURL("**/my-tasks");
    await page.getByRole("heading", { name: "My Tasks" }).waitFor();
    await page.getByText("Overdue (", { exact: false }).waitFor({ timeout: 5000 });
    await expect(page.getByText("Write copy", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Set up analytics", { exact: false }).first()).toBeVisible();
  });

  // 9) Open a task from My Tasks -> project list with peek
  await test.step("open from my-tasks → project peek", async () => {
    await page.getByRole("button", { name: "Write copy" }).first().click();
    await page.waitForURL("**/projects/**/list?task=**", { timeout: 10000 });
    await page.getByRole("dialog", { name: "Task detail" }).waitFor();
    await page.keyboard.press("Escape");
  });

  // ── M2: portfolio roll-up + status composer + board drag ──────────────────

  // 10) Create a portfolio and add the project to it
  await test.step("portfolio: create + add the project", async () => {
    await page.goto("/portfolios");
    await page.getByRole("heading", { name: "Portfolios" }).waitFor();
    // Empty state → New portfolio → modal.
    await page.getByRole("button", { name: "New portfolio" }).first().click();
    const dlg = page.getByRole("dialog", { name: "New portfolio" });
    await dlg.waitFor();
    await dlg.getByLabel("Name").fill("Q3 Delivery");
    await dlg.getByRole("button", { name: "Create portfolio" }).click();
    await page.waitForURL(/\/portfolios\/[0-9a-f-]{36}$/, { timeout: 15000 });

    // No-projects empty state → add the project via the typeahead.
    await page.getByText("Add projects to this portfolio").waitFor();
    await page.getByRole("button", { name: "+ Add projects" }).first().click();
    await page.getByPlaceholder("Search projects").fill("Launch");
    await page.getByRole("option", { name: "Launch" }).click();
    await page.keyboard.press("Escape");
    await expect(page.locator("tbody tr")).toHaveCount(1);
    // Scope to the roll-up table (the sidebar also links the project by name).
    await page.locator("tbody").getByRole("link", { name: "Launch" }).waitFor();
  });

  // 11) Post a status update via the composer (opened from the roll-up chip)
  await test.step("portfolio: post a status update via composer", async () => {
    // Chip starts grey ("No status"); clicking it opens the composer.
    await page.getByRole("button", { name: /^Status:/ }).first().click();
    const composer = page.getByRole("dialog", { name: "Update status" });
    await composer.waitFor();
    await composer.getByRole("radio", { name: "On track" }).click();
    await composer.getByLabel("Update").fill("Kickoff complete; on track for Q3.");
    await composer.getByRole("button", { name: "Post update" }).click();
    await composer.waitFor({ state: "hidden" });
    // Roll-up chip now reflects the posted status.
    await page
      .getByRole("button", { name: /Status: On track/ })
      .first()
      .waitFor({ timeout: 10000 });
  });

  // 12) Open the project board (via the view-switcher tab)
  await test.step("open the project board", async () => {
    await page.goto(`/projects/${projectId}/list`);
    await page.getByRole("navigation", { name: "Project views" }).waitFor();
    await page.getByRole("link", { name: "Board" }).click();
    await page.waitForURL("**/board", { timeout: 10000 });
    for (const s of ["To do", "In progress"]) {
      await page.getByRole("region", { name: s }).waitFor({ timeout: 10000 });
    }
    await page.getByRole("button", { name: "Open Write copy" }).waitFor();
  });

  // 13) Drag a card between columns (To do → In progress)
  await test.step("board: drag a card between columns", async () => {
    const toDo = page.getByRole("region", { name: "To do" });
    const inProg = page.getByRole("region", { name: "In progress" });
    const before = await inProg.getByRole("button", { name: /^Open / }).count();
    const card = toDo.getByRole("button", { name: "Open Write copy" });
    const cb = await card.boundingBox();
    const tb = await inProg.boundingBox();
    if (!cb || !tb) throw new Error("missing board bounding boxes");
    await page.mouse.move(cb.x + cb.width / 2, cb.y + cb.height / 2);
    await page.mouse.down();
    // Past the 4px activation threshold, then into the target column well.
    await page.mouse.move(cb.x + cb.width / 2 + 20, cb.y + cb.height / 2 + 5, { steps: 5 });
    await page.mouse.move(tb.x + tb.width / 2, tb.y + tb.height / 2, { steps: 10 });
    await page.mouse.move(tb.x + tb.width / 2, tb.y + tb.height / 2 + 10, { steps: 5 });
    await page.mouse.up();
    await page.waitForTimeout(800);
    await expect(inProg.getByRole("button", { name: "Open Write copy" })).toBeVisible();
    const after = await inProg.getByRole("button", { name: /^Open / }).count();
    expect(after, "In progress column grew after the cross-column drop").toBeGreaterThan(before);
  });

  // ── M3: subtasks + comment @mention + attachment + notification ────────────

  const bobEmail = `bob${uniq}@example.com`;
  const peek = () => page.getByRole("dialog", { name: "Task detail" });

  // 14) Seed a second org member (Bob) through the API — Ada is authenticated,
  //     so page.request carries her session cookies via the same-origin rewrite.
  await test.step("seed a second member (invite + accept)", async () => {
    const inv = await page.request.post("/api/v1/invites", { data: { email: bobEmail } });
    expect(inv.ok(), "invite created").toBeTruthy();
    const acceptUrl: string = (await inv.json()).data.acceptUrl;
    const token = new URL(acceptUrl, "http://placeholder").searchParams.get("token");
    expect(token, "invite token present in acceptUrl").toBeTruthy();
    const acc = await page.request.post("/api/v1/invites/accept", {
      data: { token, name: "Bob Builder", password: "password123" },
    });
    expect(acc.ok(), "invite accepted").toBeTruthy();
  });

  // 15) Open a task peek in the list (a fresh nav refetches the member list so
  //     the @mention typeahead knows about Bob).
  await test.step("open a task peek", async () => {
    await page.goto(`/projects/${projectId}/list`);
    await page.getByRole("button", { name: "Set up analytics" }).first().click();
    await peek().waitFor();
    await peek().getByText("Mark complete").waitFor();
  });

  // 16) Add a subtask, then complete it (progress 0/1 → 1/1).
  await test.step("add + complete a subtask", async () => {
    await peek().getByRole("button", { name: "+ Add subtask" }).click();
    await peek().getByLabel("New subtask name").fill("Draft outline");
    await peek().getByLabel("New subtask name").press("Enter");
    await peek().getByRole("button", { name: "Draft outline" }).waitFor({ timeout: 10000 });
    await peek().getByText("0/1", { exact: true }).waitFor({ timeout: 10000 });
    const subRow = peek()
      .locator("li")
      .filter({ has: page.getByRole("button", { name: "Draft outline" }) });
    await subRow.getByRole("button", { name: "Mark complete" }).click();
    await peek().getByText("1/1", { exact: true }).waitFor({ timeout: 10000 });
  });

  // 17) Post a comment that @mentions Bob (via the typeahead).
  await test.step("comment with an @mention", async () => {
    const composer = peek().getByRole("textbox", { name: "Add a comment" });
    await composer.click();
    await page.keyboard.type("Reviewing with ");
    await page.keyboard.type("@bob");
    const opt = peek().getByRole("option", { name: /Bob Builder/ });
    await opt.waitFor({ timeout: 10000 });
    await opt.click();
    await composer.focus();
    await page.keyboard.press("End");
    await page.keyboard.type("please take a look");
    await page.keyboard.press("Control+Enter");
    await peek()
      .locator("span")
      .filter({ hasText: "@Bob Builder" })
      .first()
      .waitFor({ timeout: 10000 });
  });

  // 18) Upload a small .txt attachment, then re-download it through the API's
  //     authz'd streaming route (never a direct/static URL).
  await test.step("upload + re-download a .txt attachment", async () => {
    await peek()
      .locator('input[aria-label="Attach files"]')
      .setInputFiles(path.join(FIXTURES, "note.txt"));
    await peek().getByText("note.txt", { exact: true }).waitFor({ timeout: 15000 });
    const href = await peek()
      .getByText("note.txt", { exact: true })
      .locator("xpath=ancestor::a[1]")
      .getAttribute("href");
    expect(href, "attachment download href present").toBeTruthy();
    const dl = await page.request.get(href!);
    expect(dl.status(), "download returns 200").toBe(200);
    expect(dl.headers()["content-disposition"] ?? "").toContain("attachment");
    expect(dl.headers()["x-content-type-options"] ?? "").toBe("nosniff");
    expect(await dl.text()).toContain("Cairn M3 smoke attachment");
    await peek().getByRole("button", { name: "Close" }).click();
    await peek().waitFor({ state: "hidden" });
  });

  // 19) The second user sees the @mention notification (bell badge + inbox).
  await test.step("second user sees the notification", async () => {
    const origin = new URL(page.url()).origin;
    const bobCtx = await browser.newContext({ baseURL: origin });
    const bob = await bobCtx.newPage();
    attachErrorGuard(bob, errors);
    try {
      await bob.goto("/login");
      await bob.getByLabel("Email").fill(bobEmail);
      await bob.getByLabel("Password").fill("password123");
      await bob.getByRole("button", { name: "Sign in" }).click();
      await bob.waitForURL("**/my-tasks", { timeout: 15000 });

      // Sidebar bell badge shows the unread count.
      const badge = bob
        .locator('a[href="/notifications"] span')
        .filter({ hasText: /^(\d+|9\+)$/ });
      await expect(badge.first(), "bell badge shows 1 unread").toHaveText("1", {
        timeout: 10000,
      });

      // Inbox row = the @mention sentence.
      await bob.goto("/notifications");
      await bob.getByRole("heading", { name: "Notifications" }).waitFor();
      await expect(
        bob.getByText(/mentioned you in a comment/).first(),
        "inbox shows the @mention",
      ).toBeVisible({ timeout: 10000 });
    } finally {
      await bobCtx.close();
    }
  });

  expect(errors, "no unexpected console errors during the smoke").toEqual([]);
});

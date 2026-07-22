import { test, expect, type Page } from "@playwright/test";

/**
 * Cairn M1 + M2 end-to-end smoke.
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
 * Any console.error / pageerror other than the known-benign Next.js RSC-prefetch
 * abort message fails the test.
 */

const BENIGN = [/Failed to fetch RSC payload/, /Falling back to browser navigation/];

function isoOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

test("M1 happy path: bootstrap → project → tasks → my-tasks", async ({ page }) => {
  const errors: string[] = [];
  page.on("console", (m) => {
    if (m.type() !== "error") return;
    const t = m.text();
    if (BENIGN.some((re) => re.test(t))) return;
    errors.push("console.error: " + t);
  });
  page.on("pageerror", (e) => errors.push("pageerror: " + e.message));

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

  expect(errors, "no unexpected console errors during the smoke").toEqual([]);
});

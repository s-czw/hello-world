import { test, expect, type Page } from "@playwright/test";

/**
 * Cairn M1 end-to-end smoke.
 *
 * Runs against a freshly-reset `cairn` DB with the API + web servers already up
 * (see scripts/run-e2e.mjs). Exercises the full first-run happy path:
 *   /setup bootstrap → create team+project (+3 default sections) → quick-add 3
 *   tasks → keyboard nav → inline-edit due + priority → side-peek edits
 *   (assignee/due/description) → drag + ⋯-menu reorder → complete a task (motion)
 *   → My Tasks shows the correct due-date groups → open a task from My Tasks back
 *   into the project side-peek.
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
  });

  expect(errors, "no unexpected console errors during the smoke").toEqual([]);
});

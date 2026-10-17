import { test, expect } from "@playwright/test";
import fs from "node:fs";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 568, abv: 5, kcalPerLitre: 404.15, components: [] };
const iso = (minAgo) => new Date(Date.now() - minAgo * 60_000).toISOString();
const sessions = () => [{ id: "night-a", startedAt: iso(3 * 24 * 60), intakes: [{ type: "drink", snapshot: pint, start: iso(3 * 24 * 60), duration: 30 }] }];
const read = (page, key) => page.evaluate((k) => JSON.parse(localStorage.getItem(k) || "null"), key);

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([p, s]) => {
    if (sessionStorage.getItem("seeded")) return; // keep what the test did across reloads
    sessionStorage.setItem("seeded", "1");
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
    localStorage.setItem("oh.recovery.oh.v1.sessions.1", "[broken");
  }, [profile, sessions()]);
});

async function exported(page) {
  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Export all data" }).click()]);
  expect(download.suggestedFilename()).toMatch(/^owl-hours-\d{4}-\d{2}-\d{2}\.json$/);
  return JSON.parse(fs.readFileSync(await download.path(), "utf8"));
}

test("export downloads everything as a versioned file", async ({ page }) => {
  await page.goto("/#/settings");
  const backup = await exported(page);
  expect([backup.format, backup.version, backup.profile.weight, backup.sessions.length]).toEqual(["owl-hours-backup", 1, 80, 1]);
});

test("importing a backup replaces the data, and Undo puts it back", async ({ page }) => {
  await page.goto("/#/settings");
  const backup = await exported(page);
  backup.profile.weight = 70;
  backup.sessions = [];
  await page.getByLabel("Import a backup").setInputFiles({ name: "b.json", mimeType: "application/json", buffer: Buffer.from(JSON.stringify(backup)) });
  await expect(page.getByText("Data imported")).toBeVisible();
  expect((await read(page, "oh.v1.profile")).weight).toBe(70);
  expect(await read(page, "oh.v1.sessions")).toEqual([]);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect.poll(async () => (await read(page, "oh.v1.profile")).weight).toBe(80);
  expect(await read(page, "oh.v1.sessions")).toHaveLength(1);
});

test("a file that isn't a backup changes nothing and says so", async ({ page }) => {
  await page.goto("/#/settings");
  await page.getByLabel("Import a backup").setInputFiles({ name: "photo.png", mimeType: "image/png", buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47]) });
  await expect(page.getByRole("alert").last()).toContainText("isn't an Owl Hours backup"); // Your data's alert follows the profile form's
  expect((await read(page, "oh.v1.profile")).weight).toBe(80);
});

test("delete all data asks once more, then removes everything and returns to the start", async ({ page }) => {
  await page.goto("/#/settings");
  await page.getByRole("button", { name: "Delete all data" }).click();
  await page.getByRole("button", { name: "Keep my data" }).click();
  expect(await read(page, "oh.v1.profile")).not.toBeNull();
  await page.getByRole("button", { name: "Delete all data" }).click();
  await page.getByRole("button", { name: "Delete everything" }).click();
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
  const left = await page.evaluate(() => Object.keys(localStorage).filter((k) => k.startsWith("oh.")));
  expect(left).toEqual([]);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
});

test("review: after an import or its undo, Settings shows the data now stored", async ({ page }) => {
  await page.goto("/#/settings");
  const backup = await exported(page);
  backup.profile.weight = 70;
  backup.settings.bacUnit = "MgPer100ml";
  await page.getByLabel("Import a backup").setInputFiles({ name: "b.json", mimeType: "application/json", buffer: Buffer.from(JSON.stringify(backup)) });
  await expect(page.getByLabel("Weight (kg)")).toHaveValue("70");
  await expect(page.getByLabel("mg/100 ml of blood")).toBeChecked();
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.getByLabel("Weight (kg)")).toHaveValue("80");
  await expect(page.getByLabel("‰ (per mille, blood)")).toBeChecked();
});

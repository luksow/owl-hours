import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const iso = (minAgo) => new Date(Date.now() - minAgo * 60_000).toISOString();
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 568, abv: 5, kcalPerLitre: 404.15, components: [] };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(([p, pint, a, b, c]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    localStorage.setItem("oh.v1.sessions", JSON.stringify([
      { id: "night-old", startedAt: a, intakes: [{ type: "drink", snapshot: { ...pint }, start: a, duration: 30 }] },
      { id: "night-now", startedAt: b, intakes: [
        { type: "drink", snapshot: { ...pint }, start: b, duration: 30 },
        { type: "water", volume: 250, start: c, duration: 5 } ] },
    ]));
  }, [profile, pint, iso(3 * 24 * 60), iso(90), iso(60)]);
});
const stored = (page) => page.evaluate(() => JSON.parse(localStorage.getItem("oh.v1.sessions") || "[]"));

test("the log lists every night, newest first", async ({ page }) => {
  await page.goto("/#/log");
  await expect(page.getByRole("heading", { level: 2 })).toHaveCount(2);
  await expect(page.getByRole("button", { name: /Water · 250 ml/ })).toBeVisible();
});

test("edit a past drink's amount and ABV", async ({ page }) => {
  await page.goto("/#/log");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).first().click();
  await page.getByLabel("Amount (ml)").fill("500");
  await page.getByLabel("ABV (%)").fill("5,2");
  await page.getByRole("button", { name: "Save" }).click();
  const nights = await stored(page);
  const edited = nights.at(-1).intakes.find((i) => i.type === "drink");
  expect([edited.snapshot.volume, edited.snapshot.abv]).toEqual([500, 5.2]);
});

test("delete, then undo", async ({ page }) => {
  await page.goto("/#/log");
  await page.getByRole("button", { name: /Water · 250 ml/ }).click();
  await page.getByRole("button", { name: "Delete" }).click();
  await expect(page.getByRole("button", { name: /Water · 250 ml/ })).toHaveCount(0);
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.getByRole("button", { name: /Water · 250 ml/ })).toBeVisible();
  expect((await stored(page)).at(-1).intakes).toHaveLength(2);
});

test("moving a drink to another evening regroups the nights", async ({ page }) => {
  await page.goto("/#/log");
  await page.getByRole("button", { name: /Water · 250 ml/ }).click();
  const twoDaysAgo = new Date(Date.now() - 2 * 24 * 3600_000);
  const p = (n) => String(n).padStart(2, "0");
  await page.getByLabel("Started at").fill(
    `${twoDaysAgo.getFullYear()}-${p(twoDaysAgo.getMonth() + 1)}-${p(twoDaysAgo.getDate())}T${p(twoDaysAgo.getHours())}:${p(twoDaysAgo.getMinutes())}`);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("heading", { level: 2 })).toHaveCount(3);
});

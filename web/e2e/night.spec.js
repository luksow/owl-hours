import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 568, abv: 5, kcalPerLitre: 404.15, components: [] };
const iso = (minAgo) => new Date(Date.now() - minAgo * 60_000).toISOString();
const nightOf = (minAgo) => [{ id: "night-x", startedAt: iso(minAgo), intakes: [{ type: "drink", snapshot: pint, start: iso(minAgo), duration: 30 }] }];
const stored = (page) => page.evaluate(() => JSON.parse(localStorage.getItem("oh.v1.sessions") || "[]"));

async function onboarded(page, sessions) {
  await page.addInitScript(([p, s]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
  }, [profile, sessions]);
}

test("End night is not offered while the estimate is above ~0", async ({ page }) => {
  await onboarded(page, nightOf(30));
  await page.goto("/");
  await expect(page.getByTestId("bac")).not.toHaveText("0.00‰");
  await expect(page.getByRole("button", { name: "End night" })).toHaveCount(0);
});

test("once back to ~0 the night can be ended, a new drink starts a new night, and Undo reopens it", async ({ page }) => {
  // one pint 4 hours ago: back to ~0, still inside the 2-hour wait before the automatic close
  await onboarded(page, nightOf(240));
  await page.goto("/");
  await page.getByRole("button", { name: "End night" }).click();
  await expect(page.getByRole("button", { name: "End night" })).toHaveCount(0);
  await expect.poll(async () => (await stored(page))[0].endedAt ?? null).not.toBeNull();
  await expect(page.getByRole("button", { name: "Same again" })).toBeDisabled();
  await page.getByRole("button", { name: "Undo" }).click();
  await expect(page.getByRole("button", { name: "End night" })).toBeVisible();
  await expect.poll(async () => (await stored(page))[0].endedAt ?? null).toBeNull();
});

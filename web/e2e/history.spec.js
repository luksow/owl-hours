import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 568, abv: 5, kcalPerLitre: 404.15, components: [] };
const iso = (minAgo) => new Date(Date.now() - minAgo * 60_000).toISOString();
const days = (n) => n * 24 * 60;

async function onboarded(page, sessions) {
  await page.addInitScript(([p, s]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
  }, [profile, sessions]);
}

const pastNights = () => [
  { id: "night-a", startedAt: iso(days(5)), intakes: [
    { type: "meal", kcal: 700, at: iso(days(5)) },
    { type: "drink", snapshot: pint, start: iso(days(5) - 10), duration: 90 },
    { type: "water", volume: 250, start: iso(days(5) - 60), duration: 5 } ] },
  { id: "night-b", startedAt: iso(days(2)), intakes: [{ type: "water", volume: 500, start: iso(days(2)), duration: 10 }] },
];

test("History lists past nights newest first, with badges, from Tonight's navigation", async ({ page }) => {
  await onboarded(page, pastNights());
  await page.goto("/");
  await page.getByRole("navigation", { name: "Sections" }).getByRole("button", { name: "History" }).click();
  const cards = page.getByRole("button", { name: /Peak/ });
  await expect(cards).toHaveCount(2);
  await expect(cards.first()).toContainText("Peak ~0 · 0 drinks");
  await expect(cards.nth(1)).toContainText(/Peak 0\.\d\d‰ · 1 drink · 2\.2 standard drinks/);
  for (const badge of ["Hydration hero", "Fed & steady", "Slow sipper"]) await expect(cards.nth(1)).toContainText(badge);
});

test("a night opens to its chart without a now marker, and badges explain themselves", async ({ page }) => {
  await onboarded(page, pastNights());
  await page.goto("/#/history");
  await page.getByRole("button", { name: /1 drink/ }).click();
  await expect(page.locator('svg[aria-label^="Blood alcohol estimate on"]')).toBeVisible();
  await expect(page.locator("svg .oh-now")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Slow sipper" })).toBeVisible();
  await expect(page.getByText("Never more than 15 g of alcohol in any hour.")).toBeVisible();
  await page.getByRole("button", { name: "Back" }).click();
  await expect(page).toHaveURL(/#\/history$/);
});

test("a stale link to a night that is gone explains itself", async ({ page }) => {
  await onboarded(page, pastNights());
  await page.goto("/#/history/night-1");
  await expect(page.getByText("That night isn't in your history any more.", { exact: false })).toBeVisible();
});

test("the open night is not in History", async ({ page }) => {
  await onboarded(page, [{ id: "night-now", startedAt: iso(30), intakes: [{ type: "drink", snapshot: pint, start: iso(30), duration: 30 }] }]);
  await page.goto("/#/history");
  await expect(page.getByText("No past nights yet.", { exact: false })).toBeVisible();
});

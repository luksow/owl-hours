import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 568, abv: 5, kcalPerLitre: 404.15, components: [] };

async function onboarded(page, sessions) {
  await page.addInitScript(([p, s]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    if (s) localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
  }, [profile, sessions]);
}

async function pickPils(page) {
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
}

test("the preview shows the peak and back-to-~0 for the first drink", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await pickPils(page);
  const preview = page.getByTestId("what-if");
  await expect(preview.getByText(/^Peak \d\.\d\d‰ at \d\d:\d\d/)).toBeVisible();
  await expect(preview.locator("svg .oh-projection, svg .oh-past")).not.toHaveCount(0);
  await expect(preview).not.toContainText(/safe|fine|okay|one more|you can/i);
});

test("with drinks already logged the preview compares before → after and draws a ghost", async ({ page }) => {
  const at = new Date(Date.now() - 45 * 60_000).toISOString();
  await onboarded(page, [{ id: "night-x", startedAt: at, intakes: [{ type: "drink", snapshot: pint, start: at, duration: 30 }] }]);
  await page.goto("/");
  await pickPils(page);
  const preview = page.getByTestId("what-if");
  await expect(preview.getByText(/^Peak \d\.\d\d‰ → \d\.\d\d‰/)).toBeVisible();
  await expect(preview.locator("svg .oh-ghost")).toHaveCount(1);
});

test("the preview follows the amount and clears on bad input; Add still explains the error", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await pickPils(page);
  const preview = page.getByTestId("what-if");
  const small = await preview.getByText(/^Peak/).textContent();
  await page.getByLabel("Amount (ml)").fill("1000");
  await expect(preview.getByText(/^Peak/)).not.toHaveText(small);
  await page.getByLabel("Amount (ml)").fill("abc");
  await expect(preview.getByText(/^Peak/)).toHaveCount(0);
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText("Amount must be a number")).toBeVisible();
});

import { test, expect } from "@playwright/test";

const profile = { sex: "Female", age: 30, weight: 62, height: 166 };
test.beforeEach(async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
});
const stored = (page) => page.evaluate(() => JSON.parse(localStorage.getItem("oh.v1.sessions") || "[]"));

test("water: choose how much", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Water" }).click();
  await page.getByRole("button", { name: "500 ml" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByTestId("bac")).toBeVisible();
  const w = (await stored(page))[0].intakes[0];
  expect([w.type, w.volume]).toEqual(["water", 500]);
});

test("food: a custom amount of kcal, earlier in the evening", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Food" }).click();
  await page.getByLabel("Energy (kcal)").fill("650");
  await page.getByRole("button", { name: "30 min ago" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  const m = (await stored(page))[0].intakes[0];
  expect([m.type, m.kcal]).toEqual(["meal", 650]);
  expect(Date.now() - new Date(m.at).getTime()).toBeGreaterThan(29 * 60_000);
});

test("food: invalid kcal is explained", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Food" }).click();
  await page.getByLabel("Energy (kcal)").fill("9000");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText("Energy must be between 0 and 5000 kcal, got 9000")).toBeVisible();
  expect(await stored(page)).toEqual([]);
});

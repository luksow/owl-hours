import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };

test.beforeEach(async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
});

test("log a pint an hour ago: BAC above zero, peak chip, Same again enabled", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("bac")).toHaveText("0.00‰");
  await expect(page.getByRole("button", { name: "Same again" })).toBeDisabled();

  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  await page.getByRole("button", { name: "UK pint · 568 ml" }).click();
  await page.getByRole("button", { name: "60 min ago" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();

  await expect(page.getByTestId("bac")).not.toHaveText("0.00‰");
  await expect(page.getByTestId("chips").getByText("Peak", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Same again" })).toBeEnabled();
  await expect(page.locator("svg .oh-past")).toHaveCount(1);
});

test("water and food are two taps away", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Water" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await page.getByRole("button", { name: "Food" }).click();
  await page.getByRole("button", { name: /Full meal/ }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.locator("svg .oh-marker")).toHaveCount(2);
});

test("cancel returns without logging", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page.getByTestId("bac")).toHaveText("0.00‰");
  await expect(page.locator("svg .oh-marker")).toHaveCount(0);
});

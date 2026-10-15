import { test, expect } from "@playwright/test";

test("first run: disclaimer, then profile, then Tonight", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Continue" })).toBeDisabled();
  await page.getByLabel("I understand").check();
  await page.getByRole("button", { name: "Continue" }).click();

  await page.getByLabel("Male", { exact: true }).check();
  await page.getByLabel("Date of birth").fill("1996-04-01");
  await page.getByLabel("Weight (kg)").fill("80");
  await page.getByLabel("Height (cm)").fill("180");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Tonight", { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByText("Tonight", { exact: true })).toBeVisible(); // remembered
});

test("profile errors are shown together", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("I understand").check();
  await page.getByRole("button", { name: "Continue" }).click();
  const seventeen = new Date();
  seventeen.setFullYear(seventeen.getFullYear() - 17);
  await page.getByLabel("Date of birth").fill(seventeen.toISOString().slice(0, 10));
  await page.getByLabel("Weight (kg)").fill("abc");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Choose the sex the model should use")).toBeVisible();
  await expect(page.getByText("Owl Hours is for adults (18+)")).toBeVisible();
  await expect(page.getByText("Weight must be a number")).toBeVisible();
  await expect(page.getByText("Height must be a number")).toBeVisible();
});

test("profile errors are announced to screen readers", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("I understand").check();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("alert")).toContainText("Choose the sex the model should use");
});

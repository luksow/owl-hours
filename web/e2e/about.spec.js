import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };

test.beforeEach(async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
});

test("About explains the model, the band, the limits, privacy and credits", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("navigation", { name: "Sections" }).getByRole("button", { name: "About" }).click();
  for (const h of ["How the estimate works", "The shaded band", "What it can't know", "Never for driving", "Privacy", "Credits"])
    await expect(page.getByRole("heading", { name: h })).toBeVisible();
  await expect(page.getByText("typical person-to-person variation", { exact: false })).toBeVisible();
  await expect(page.getByText("No number on this app means it is safe to drive.")).toBeVisible();
  await expect(page.getByText("No accounts, no analytics, no cookies", { exact: false })).toBeVisible();
  await expect(page.getByText("Model inside — Podéus & Cedersund, Linköping University")).toBeVisible();
  await expect(page.getByText("npj Digital Medicine", { exact: false })).toBeVisible();
  const text = await page.locator("#app").innerText();
  expect(text).not.toMatch(/confidence interval|safe to drive(?!\.)|you can have one more|level up|lightweight/i);
});

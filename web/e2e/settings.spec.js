import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const read = (page, key) => page.evaluate((k) => JSON.parse(localStorage.getItem(k) || "null"), key);

test.beforeEach(async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
});

test("the profile is prefilled and saving a change stores it", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("navigation", { name: "Sections" }).getByRole("button", { name: "Settings" }).click();
  await expect(page.getByLabel("Weight (kg)")).toHaveValue("80");
  await expect(page.getByLabel("Date of birth")).toHaveValue("1996-01-01");
  await expect(page.getByLabel("Male", { exact: true })).toBeChecked();
  await page.getByLabel("Weight (kg)").fill("85,5");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByText("Saved.")).toBeVisible();
  expect((await read(page, "oh.v1.profile")).weight).toBe(85.5);
});

test("an invalid profile is not saved and says why", async ({ page }) => {
  await page.goto("/#/settings");
  await page.getByLabel("Weight (kg)").fill("abc");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("alert").first()).toContainText("Weight must be a number"); // the profile form's alert comes before Your data's
  expect((await read(page, "oh.v1.profile")).weight).toBe(80);
});

test("choosing a unit changes Tonight; the standard drink is stored", async ({ page }) => {
  await page.goto("/#/settings");
  await page.getByLabel("mg/100 ml of blood").check();
  await page.getByLabel("8 g — UK unit").check();
  await expect.poll(async () => {
    const s = await read(page, "oh.v1.settings");
    return s && [s.bacUnit, s.standardDrink];
  }).toEqual(["MgPer100ml", "Grams8"]);
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByTestId("bac")).toHaveText(/mg\/100 ml/);
});

import { test, expect } from "@playwright/test";

test.use({ serviceWorkers: "block" });

test("a broken catalogue shows every problem instead of a blank screen", async ({ page }) => {
  await page.route("**/catalogue.json", (route) =>
    route.fulfill({ contentType: "application/json", body: '{"schemaVersion": 2, "styles": [], "products": [], "cocktails": []}' }),
  );
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "The drink list could not be loaded" })).toBeVisible();
  await expect(page.getByText("unsupported schemaVersion 2 (expected 1)")).toBeVisible();
});

test("unreadable saved data is reported, with a recovery copy kept", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("oh.v1.profile", '{"sex":"Female","age":12}'));
  await page.goto("/");
  await expect(page.getByText(/Could not read your profile/)).toBeVisible();
  const keys = await page.evaluate(() => Object.keys(localStorage));
  expect(keys.some((k) => k.startsWith("oh.recovery.oh.v1.profile."))).toBeTruthy();
});

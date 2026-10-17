import { test, expect } from "@playwright/test";

const profile = { sex: "Female", born: "1996-01-01", weight: 62, height: 166 };
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

test("the time field is shown from the start and the chips fill it", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify({ sex: "Female", born: "1995-05-05", weight: 64, height: 168 }));
  });
  await page.goto("/#/water");
  const field = page.getByLabel("Started at");
  await expect(field).toBeVisible();
  await expect(page.getByRole("button", { name: "Pick time" })).toHaveCount(0);
  const nowText = await field.inputValue();
  await page.getByRole("button", { name: "30 min ago" }).click();
  await expect(page.getByRole("button", { name: "30 min ago" })).toHaveAttribute("aria-pressed", "true");
  const thirty = await field.inputValue();
  expect(new Date(nowText) - new Date(thirty)).toBe(30 * 60_000);
  await field.fill(thirty.slice(0, 11) + "00:05");
  await expect(page.getByRole("button", { name: "30 min ago" })).toHaveAttribute("aria-pressed", "false");
});

test.describe("desktop keyboard editing", () => {
  test.use({ viewport: { width: 1280, height: 800 }, isMobile: false, hasTouch: false, locale: "en-US" });

  test("final review: clearing one segment of the time and retyping it keeps the rest", async ({ page }) => {
    await page.goto("/#/water");
    const field = page.getByLabel("Started at");
    await page.getByRole("button", { name: "30 min ago" }).click();
    const before = await field.inputValue(); // yyyy-MM-ddTHH:mm
    await field.click({ position: { x: 14, y: 24 } }); // a date segment
    await page.keyboard.press("Backspace");
    await expect(field).toHaveValue(""); // one segment empty makes the whole value empty
    await page.keyboard.type("10"); // retype a value into the cleared segment
    // the other segments survive: before the fix, echoing "" back made Chromium clear them all
    await expect(field).not.toHaveValue("");
    const after = await field.inputValue();
    expect(after.slice(0, 4)).toBe(before.slice(0, 4));
    expect(after.slice(11)).toBe(before.slice(11));
  });
});

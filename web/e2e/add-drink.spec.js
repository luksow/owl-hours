import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };

test.beforeEach(async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
});

const stored = (page) => page.evaluate(() => JSON.parse(localStorage.getItem("oh.v1.sessions") || "[]"));

function localInput(date) {
  const p = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}

test("a hazy IPA can be logged as a 500 ml can", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("hazy");
  await page.getByRole("button", { name: /NEIPA \/ hazy IPA/ }).click();
  await page.getByRole("button", { name: "can / bottle · 500 ml" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  const [night] = await stored(page);
  expect(night.intakes[0].snapshot.volume).toBe(500);
});

test("custom amount, a picked start time and a custom drinking time", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  await page.getByLabel("Amount (ml)").fill("400");
  const started = new Date(Date.now() - 90 * 60_000);
  await page.getByLabel("Started at").fill(localInput(started));
  await page.getByLabel("Drinking time (min)").fill("45");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByTestId("bac")).toBeVisible();
  const intake = (await stored(page))[0].intakes[0];
  expect(intake.snapshot.volume).toBe(400);
  expect(intake.duration).toBe(45);
  expect(Math.abs(new Date(intake.start).getTime() - started.getTime())).toBeLessThan(60_000);
});

test("a cocktail's amount scales every ingredient", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).click();
  await page.getByRole("button", { name: "×1.5" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  const snapshot = (await stored(page))[0].intakes[0].snapshot;
  expect(snapshot.components.map((c) => c.volume)).toEqual([45, 45, 45]);
});

test("bad input is explained and nothing is logged", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  await page.getByLabel("Amount (ml)").fill("abc");
  await page.getByLabel("Drinking time (min)").fill("");
  await page.getByLabel("Started at").fill(localInput(new Date(Date.now() + 3 * 3600_000)));
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText("Amount must be a number")).toBeVisible();
  await expect(page.getByText("Duration must be a number")).toBeVisible();
  await expect(page.getByText("That's later than now — for last night, pick yesterday's date")).toBeVisible();
  expect(await stored(page)).toEqual([]);
});

test("a typed start time survives the clock's tick", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  const started = new Date(Date.now() - 120 * 60_000);
  await page.getByLabel("Started at").fill(localInput(started));
  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange"))); // the app re-reads the clock
  await expect(page.getByLabel("Started at")).toHaveValue(localInput(started));
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByTestId("bac")).toBeVisible();
  const intake = (await stored(page))[0].intakes[0];
  expect(Math.abs(new Date(intake.start).getTime() - started.getTime())).toBeLessThan(60_000);
});

test("a negroni can be adjusted ingredient by ingredient", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).click();
  await page.getByRole("button", { name: "Adjust ingredients" }).click();
  await page.getByLabel("Gin (London Dry) (ml)").fill("50");
  await page.getByLabel("Sweet vermouth (ml)").fill("0");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  const snapshot = (await stored(page))[0].intakes[0].snapshot;
  expect(snapshot.name).toBe("Negroni (custom)");
  expect(snapshot.components.map((c) => [c.name, c.volume])).toEqual([["Gin (London Dry)", 50], ["Campari", 30]]);
});

test("the drinking time follows the size until you change it", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  await page.getByRole("button", { name: "UK pint · 568 ml" }).click();
  await expect(page.getByLabel("Drinking time (min)")).toHaveValue("30");
  await page.getByLabel("Drinking time (min)").fill("45");
  await page.getByRole("button", { name: "bottle · 330 ml" }).click();
  await expect(page.getByLabel("Drinking time (min)")).toHaveValue("45");
});

test("a custom cocktail amount is named as custom", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).click();
  await page.getByLabel("Amount (ml)").fill("120");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  expect((await stored(page))[0].intakes[0].snapshot.name).toBe("Negroni (custom)");
});

test("Adjust ingredients opens and closes, and closing goes back to the amount", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).click();
  const toggle = page.getByRole("button", { name: "Adjust ingredients" });
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await toggle.click();
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await expect(page.getByLabel("Campari (ml)")).toBeVisible();
  await toggle.click();
  await expect(page.getByLabel("Campari (ml)")).toHaveCount(0);
  await expect(page.getByLabel("Amount (ml)")).toBeVisible();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  expect((await stored(page))[0].intakes[0].snapshot.name).not.toContain("custom");
});

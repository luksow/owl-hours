import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };

async function onboarded(page) {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
}

async function countChartRenders(page) {
  await page.evaluate(() => {
    window.__chartRenders = 0;
    new MutationObserver((records) => {
      for (const r of records)
        for (const n of r.addedNodes)
          if (n.nodeType === 1) window.__chartRenders += (n.nodeName === "svg" ? 1 : 0) + n.querySelectorAll("svg").length;
    }).observe(document.body, { childList: true, subtree: true });
  });
}

test("a tap renders the chart once and keeps the screen (no rebuild)", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.locator("svg .oh-marker")).toHaveCount(1);
  await page.evaluate(() => document.querySelector("header").setAttribute("data-probe", "kept"));
  await countChartRenders(page);
  await page.getByRole("button", { name: "Same again" }).click();
  await expect(page.locator("svg .oh-marker")).toHaveCount(2);
  await page.waitForTimeout(300);
  expect(await page.evaluate(() => window.__chartRenders)).toBe(1);
  await expect(page.locator("header[data-probe=kept]")).toHaveCount(1);
});

test("a clock tick renders the chart once", async ({ page }) => {
  await page.clock.install();
  await onboarded(page);
  await page.goto("/");
  await expect(page.getByTestId("bac")).toBeVisible();
  await page.getByRole("button", { name: "Water" }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.locator("svg .oh-marker")).toHaveCount(1);
  await countChartRenders(page);
  await page.clock.runFor(31_000);
  await page.waitForTimeout(300);
  expect(await page.evaluate(() => window.__chartRenders)).toBe(1);
});

test("the ABV slider can be dragged all the way", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByLabel("Search drinks").fill("pils");
  await page.getByRole("button", { name: /Pale lager \/ Pilsner/ }).click();
  const box = await page.getByLabel("ABV").boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  for (let i = 1; i <= 10; i++) await page.mouse.move(box.x + box.width / 2 + (box.width / 2 + 20) * (i / 10), box.y + box.height / 2);
  await page.mouse.up();
  await expect(page.getByText("ABV 6 %")).toBeVisible();
});

test("drinks can be found by group without typing, recents first", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await page.getByRole("button", { name: "+ Drink" }).click();
  await page.getByRole("button", { name: "Cocktails", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Negroni/ })).toBeVisible();
  await page.getByRole("button", { name: /^Negroni/ }).click();
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await page.getByRole("button", { name: "+ Drink" }).click();
  await expect(page.getByRole("list").getByRole("button").first()).toContainText("Negroni");
});

test("storage that is blocked is reported", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, "localStorage", { get() { throw new DOMException("blocked", "SecurityError"); } });
  });
  await page.goto("/");
  await expect(page.getByText(/can't save on this device/)).toBeVisible();
});

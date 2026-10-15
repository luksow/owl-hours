import { test, expect } from "@playwright/test";

test("after one visit the app, catalogue and fonts load with the network off", async ({ page, context }) => {
  await page.goto("/");
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload(); // now controlled by the service worker
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
  const fontsLoaded = await page.evaluate(async () => {
    await document.fonts.ready;
    return [...document.fonts].some((f) => f.family.includes("Mr Dafoe") && f.status === "loaded");
  });
  expect(fontsLoaded).toBeTruthy();
});

test("a waiting new version shows a reload prompt instead of reloading", async ({ page }) => {
  await page.goto("/");
  await page.evaluate(() => window.dispatchEvent(new CustomEvent("oh-update-ready")));
  await expect(page.getByText("A new version of Owl Hours is ready.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Reload" })).toBeVisible();
});

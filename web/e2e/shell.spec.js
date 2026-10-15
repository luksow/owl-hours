import { test, expect } from "@playwright/test";

test("the app shell renders with self-hosted brand fonts and serves the catalogue", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.locator("#app > *")).not.toHaveCount(0);
  const loaded = await page.evaluate(async () => (await document.fonts.load('32px "Mr Dafoe"')).length > 0);
  expect(loaded).toBeTruthy();
  const catalogue = await request.get("/catalogue.json");
  expect(catalogue.ok()).toBeTruthy();
  expect((await catalogue.json()).schemaVersion).toBe(1);
});

test("the manifest makes the app installable", async ({ request }) => {
  const manifest = await (await request.get("/manifest.webmanifest")).json();
  expect(manifest.name).toBe("Owl Hours");
  expect(manifest.icons.some((i) => i.purpose === "maskable")).toBeTruthy();
});

test("the service worker precaches the app bundle and only latin fonts; iOS tags exist", async ({ page, request }) => {
  const sw = await (await request.get("/sw.js")).text();
  expect(sw).toMatch(/assets\/index-[^"']+\.js/);
  expect(sw).not.toMatch(/vietnamese|cyrillic|greek/);
  await page.goto("/");
  await expect(page.locator('meta[name="apple-mobile-web-app-capable"]')).toHaveAttribute("content", "yes");
  await expect(page.locator('meta[name="apple-mobile-web-app-status-bar-style"]')).toHaveAttribute("content", "black-translucent");
});

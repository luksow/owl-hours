import { test, expect } from "@playwright/test";

test("under a sub-path the app boots, loads its catalogue and links every asset under that path", async ({ page }) => {
  await page.goto("./");
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible(); // the catalogue loaded
  const hrefs = await page.locator('link[rel~="icon"], link[rel="apple-touch-icon"], link[rel="manifest"], link[rel="apple-touch-startup-image"]')
    .evaluateAll((ls) => ls.map((l) => new URL(l.getAttribute("href"), location.href).pathname));
  expect(hrefs.length).toBeGreaterThan(10);
  for (const h of hrefs) expect(h).toMatch(/^\/owl-hours\//);
});

test("the manifest and service worker are scoped to the sub-path, and it works offline", async ({ page, context, request }) => {
  const manifest = await (await request.get("manifest.webmanifest")).json();
  expect(manifest.scope).toBe("/owl-hours/");
  await page.goto("./");
  const scope = await page.evaluate(async () => (await navigator.serviceWorker.ready).scope);
  expect(new URL(scope).pathname).toBe("/owl-hours/");
  await page.reload();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
});

test("link previews get an absolute image URL on the site", async ({ page }) => {
  await page.goto("./");
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute("content", "https://owlhours.example/owl-hours/og-image.png");
  await expect(page.locator('meta[property="og:url"]')).toHaveAttribute("content", "https://owlhours.example/owl-hours/");
});

import { test, expect } from "@playwright/test";

/** Width and height from a PNG's IHDR chunk. */
const pngSize = (buf) => [buf.readUInt32BE(16), buf.readUInt32BE(20)];

test("every manifest icon is a PNG of the size it declares", async ({ request }) => {
  const manifest = await (await request.get("/manifest.webmanifest")).json();
  for (const icon of manifest.icons) {
    const res = await request.get("/" + icon.src);
    expect(res.ok(), icon.src).toBeTruthy();
    const [w, h] = pngSize(await res.body());
    expect(`${w}x${h}`, icon.src).toBe(icon.sizes);
  }
  const apple = await request.get("/apple-touch-icon-180x180.png");
  expect(pngSize(await apple.body())).toEqual([180, 180]);
});

test("the favicon is an ICO holding 16, 32 and 48 px images", async ({ request }) => {
  const buf = await (await request.get("/favicon.ico")).body();
  expect(buf.readUInt16LE(2)).toBe(1); // type: icon
  const sizes = Array.from({ length: buf.readUInt16LE(4) }, (_, i) => buf.readUInt8(6 + 16 * i));
  expect(sizes).toEqual([16, 32, 48]);
});

test("the maskable icon keeps all of the owl inside the central 80 %", async ({ page }) => {
  await page.goto("/icon.svg");
  const worst = await page.evaluate(() => {
    const root = document.documentElement;
    let max = 0;
    for (const path of document.querySelectorAll("[data-safe] path")) {
      const m = root.getScreenCTM().inverse().multiply(path.getScreenCTM());
      const len = path.getTotalLength();
      for (let i = 0; i <= 200; i++) {
        const p = path.getPointAtLength((len * i) / 200).matrixTransform(m);
        max = Math.max(max, Math.hypot(p.x - 50, p.y - 50));
      }
    }
    return max;
  });
  expect(worst).toBeGreaterThan(20); // the measurement found the art
  expect(worst).toBeLessThanOrEqual(38); // radius 40, minus half a tube
});

test("the favicon, the app icon and the in-app lockup draw the same owl head", async ({ page, request }) => {
  const headOf = async (url) =>
    page.evaluate((text) => new DOMParser().parseFromString(text, "image/svg+xml").querySelector('[data-part="head"]')?.getAttribute("d") ?? null,
      await (await request.get(url)).text());
  await page.goto("/");
  const inApp = await page.locator('svg[aria-label="Owl Hours"] .oh-head').first().getAttribute("d");
  expect(await headOf("/owl.svg")).toBe(inApp);
  expect(await headOf("/icon.svg")).toBe(inApp);
});

test("every iOS splash link matches its device exactly and points at an image of that size", async ({ page, request }) => {
  await page.goto("/");
  const links = await page.locator('link[rel="apple-touch-startup-image"]').evaluateAll((ls) =>
    ls.map((l) => ({ href: l.getAttribute("href"), media: l.getAttribute("media") })));
  expect(links.length).toBeGreaterThanOrEqual(10);
  for (const { href, media } of links) {
    const [, w, h, r] = media.match(/device-width: (\d+)px\) and \(device-height: (\d+)px\) and \(-webkit-device-pixel-ratio: (\d)\) and \(orientation: portrait\)/);
    const res = await request.get(href);
    expect(res.ok(), href).toBeTruthy();
    expect(pngSize(await res.body()), href).toEqual([w * r, h * r]);
  }
});

test("the social preview is 1200 × 630 and the page describes itself to link previews", async ({ page, request }) => {
  expect(pngSize(await (await request.get("/og-image.png")).body())).toEqual([1200, 630]);
  await page.goto("/");
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute("content", "Owl Hours");
  await expect(page.locator('meta[property="og:description"]')).toHaveAttribute("content", /never for driving decisions/);
  await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute("content", "summary_large_image");
});

test("splash screens and the social preview stay out of the offline precache", async ({ request }) => {
  const sw = await (await request.get("/sw.js")).text();
  expect(sw).not.toMatch(/apple-splash|og-image/);
  expect(sw).toMatch(/icon\.svg/);
});

test("the two identical 512 px install icons are not precached for every visitor", async ({ request }) => {
  const sw = await (await request.get("/sw.js")).text();
  expect(sw).not.toMatch(/512x512\.png/); // the browser fetches them itself when the app is installed
  expect(sw).toMatch(/pwa-192x192\.png/);
  expect((await request.get("/maskable-icon-512x512.png")).status()).toBe(200);
});

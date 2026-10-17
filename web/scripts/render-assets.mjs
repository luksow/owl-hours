// Renders the brand PNGs (spec §8.6) with Playwright's Chromium, so filters and the self-hosted fonts render exactly
// as in the app. Output is committed; run `npm run build && npm run assets` after changing web/public/icon.svg, owl.svg,
// web/brand/ or how Tonight draws Watt and the curve (the social preview is captured from the built app).
import { chromium } from "@playwright/test";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { splashScreens, splashFile } from "../brand/splash-sizes.js";

const web = path.resolve(import.meta.dirname, "..");
const out = (f) => path.join(web, "public", f);
const types = { ".html": "text/html", ".svg": "image/svg+xml", ".js": "text/javascript", ".woff2": "font/woff2", ".css": "text/css",
  ".png": "image/png", ".json": "application/json", ".webmanifest": "application/manifest+json", ".ico": "image/x-icon" };

/** A static server over `root`, so pages load fonts and images over http (file:// blocks web fonts). */
function serve(root = web) {
  const server = http.createServer((req, res) => {
    let file = path.join(root, decodeURIComponent(new URL(req.url, "http://x").pathname));
    if (file.endsWith(path.sep)) file = path.join(file, "index.html");
    if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return res.writeHead(404).end();
    res.writeHead(200, { "Content-Type": types[path.extname(file)] ?? "application/octet-stream" }).end(fs.readFileSync(file));
  });
  return new Promise((resolve) => server.listen(0, () => resolve(server)));
}

const dist = path.join(web, "dist");
if (!fs.existsSync(path.join(dist, "index.html"))) {
  console.error("Run `npm run build` first: the social preview is captured from the built app.");
  process.exit(1);
}

const server = await serve();
const origin = `http://localhost:${server.address().port}`;
const browser = await chromium.launch();

/** A screenshot of `url` at width × height CSS pixels and the given device pixel ratio. */
async function shoot(url, width, height, scale = 1) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: scale });
  await page.goto(origin + url);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((i) => i.decode()));
  });
  const png = await page.screenshot({ omitBackground: true });
  await page.close();
  return png;
}

const frame = (src, size) => shoot(`/brand/frame.html?src=${encodeURIComponent(src)}&w=${size}`, size, size);

/** An ICO file whose entries are PNG images (allowed since Windows Vista, read by every browser). */
function ico(pngs) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(pngs.length, 4);
  let offset = 6 + 16 * pngs.length;
  const entries = pngs.map(({ size, png }) => {
    const e = Buffer.alloc(16);
    e.writeUInt8(size, 0);
    e.writeUInt8(size, 1);
    e.writeUInt16LE(1, 4); // colour planes
    e.writeUInt16LE(32, 6); // bits per pixel
    e.writeUInt32LE(png.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += png.length;
    return e;
  });
  return Buffer.concat([header, ...entries, ...pngs.map((p) => p.png)]);
}

for (const size of [64, 192, 512]) fs.writeFileSync(out(`pwa-${size}x${size}.png`), await frame("/public/icon.svg", size));
fs.writeFileSync(out("maskable-icon-512x512.png"), await frame("/public/icon.svg", 512));
fs.writeFileSync(out("apple-touch-icon-180x180.png"), await frame("/public/icon.svg", 180));
const favicons = [];
for (const size of [16, 32, 48]) favicons.push({ size, png: await frame("/public/owl.svg", size) });
fs.writeFileSync(out("favicon.ico"), ico(favicons));

// The social preview shows the product (spec §8): Watt and a real curve, captured from the built app.
{
  const app = await serve(dist);
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3 });
  await page.emulateMedia({ reducedMotion: "reduce" }); // Watt holds still
  const iso = (m) => new Date(Date.now() - m * 60_000).toISOString();
  const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 500, abv: 5, kcalPerLitre: 404.15, components: [] };
  const night = [{ id: "og", startedAt: iso(45), intakes: [
    { type: "drink", snapshot: pint, start: iso(45), duration: 15 },
    { type: "water", volume: 330, start: iso(20), duration: 5 },
    { type: "drink", snapshot: pint, start: iso(5), duration: 30 } ] }];
  await page.addInitScript((s) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify({ sex: "Female", born: "1995-05-05", weight: 64, height: 168 }));
    localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
    localStorage.setItem("oh.v1.installHint", "dismissed");
  }, night);
  await page.goto(`http://localhost:${app.address().port}/`);
  const state = await page.getByTestId("state").textContent({ timeout: 30_000 });
  if (state !== "Warming up") throw new Error(`the social preview's night should read "Warming up", got "${state}"`);
  await page.evaluate(() => document.fonts.ready);
  // an element screenshot includes whatever is painted behind it; clear every CSS background (SVG fills are untouched)
  await page.addStyleTag({ content: "*, *::before, *::after { background: transparent !important; border-color: transparent !important; box-shadow: none !important; }" });
  await page.locator("[data-testid=watt] svg").screenshot({ path: path.join(web, "brand", "og-watt.png"), omitBackground: true });
  await page.locator("[data-testid=chart-card] svg").screenshot({ path: path.join(web, "brand", "og-curve.png"), omitBackground: true });
  await page.close();
  app.close();
}
fs.writeFileSync(out("og-image.png"), await shoot("/brand/poster.html?kind=social", 1200, 630));
fs.mkdirSync(out("splash"), { recursive: true });
for (const s of splashScreens) fs.writeFileSync(out(splashFile(s)), await shoot("/brand/poster.html?kind=splash", s.w, s.h, s.r));

await browser.close();
server.close();
console.log("brand assets rendered into web/public");

// Renders the brand PNGs (spec §8.6) with Playwright's Chromium, so filters and the self-hosted fonts render exactly
// as in the app. Output is committed; run `npm run assets` after changing web/public/icon.svg, owl.svg or web/brand/.
import { chromium } from "@playwright/test";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";

const web = path.resolve(import.meta.dirname, "..");
const out = (f) => path.join(web, "public", f);
const types = { ".html": "text/html", ".svg": "image/svg+xml", ".js": "text/javascript", ".woff2": "font/woff2", ".css": "text/css", ".png": "image/png" };

/** A static server over web/, so pages load fonts and images over http (file:// blocks web fonts). */
function serve() {
  const server = http.createServer((req, res) => {
    const file = path.join(web, decodeURIComponent(new URL(req.url, "http://x").pathname));
    if (!file.startsWith(web) || !fs.existsSync(file)) return res.writeHead(404).end();
    res.writeHead(200, { "Content-Type": types[path.extname(file)] ?? "application/octet-stream" }).end(fs.readFileSync(file));
  });
  return new Promise((resolve) => server.listen(0, () => resolve(server)));
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

await browser.close();
server.close();
console.log("brand assets rendered into web/public");

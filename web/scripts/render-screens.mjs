// Phone screenshots of the built app for the README, at a fixed evening, locale and time zone so they don't depend on
// who renders them, or when. Output is committed; run `npm run build && npm run screens` after a visible change.
import { chromium } from "@playwright/test";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";

const web = path.resolve(import.meta.dirname, "..");
const dist = path.join(web, "dist");
const outDir = path.resolve(web, "..", ".github", "readme");
const types = { ".html": "text/html", ".svg": "image/svg+xml", ".js": "text/javascript", ".woff2": "font/woff2", ".css": "text/css",
  ".png": "image/png", ".json": "application/json", ".webmanifest": "application/manifest+json", ".ico": "image/x-icon" };

if (!fs.existsSync(path.join(dist, "index.html"))) {
  console.error("Run `npm run build` first: the screenshots are captured from the built app.");
  process.exit(1);
}

const server = http.createServer((req, res) => {
  let file = path.join(dist, decodeURIComponent(new URL(req.url, "http://x").pathname));
  if (file.endsWith(path.sep)) file = path.join(file, "index.html");
  if (!file.startsWith(dist) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return res.writeHead(404).end();
  res.writeHead(200, { "Content-Type": types[path.extname(file)] ?? "application/octet-stream" }).end(fs.readFileSync(file));
});
await new Promise((resolve) => server.listen(0, resolve));
const origin = `http://localhost:${server.address().port}`;

const evening = Date.parse("2026-10-16T22:50:00Z"); // a Friday, 23:50 in London
const iso = (minutesAgo) => new Date(evening - minutesAgo * 60_000).toISOString();
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 500, abv: 5, kcalPerLitre: 404.15, components: [] };
const wine = { catalogueId: "red-wine-dry", name: "Dry red", volume: 175, abv: 13.5, kcalPerLitre: 849, components: [] };
const bottle = { ...pint, volume: 330 };
const day = 1440;
// oldest first, as the app stores them
const sessions = [
  { id: "last-week", startedAt: iso(7 * day + 60), intakes: [
    { type: "drink", snapshot: wine, start: iso(7 * day + 60), duration: 20 },
    { type: "water", volume: 330, start: iso(7 * day + 30), duration: 5 },
    { type: "drink", snapshot: wine, start: iso(7 * day + 10), duration: 25 } ] },
  { id: "saturday", startedAt: iso(6 * day + 240), intakes: [
    { type: "meal", kcal: 700, at: iso(6 * day + 240) },
    { type: "drink", snapshot: bottle, start: iso(6 * day + 220), duration: 30 },
    { type: "water", volume: 330, start: iso(6 * day + 180), duration: 5 },
    { type: "drink", snapshot: bottle, start: iso(6 * day + 140), duration: 30 },
    { type: "water", volume: 330, start: iso(6 * day + 100), duration: 5 } ] },
  { id: "wednesday", startedAt: iso(2 * day + 200), intakes: [
    { type: "drink", snapshot: wine, start: iso(2 * day + 200), duration: 20 },
    { type: "drink", snapshot: wine, start: iso(2 * day + 160), duration: 20 },
    { type: "water", volume: 250, start: iso(2 * day + 130), duration: 5 } ] },
  { id: "tonight", startedAt: iso(170), intakes: [
    { type: "meal", kcal: 800, at: iso(170) },
    { type: "drink", snapshot: pint, start: iso(150), duration: 30 },
    { type: "water", volume: 330, start: iso(105), duration: 5 },
    { type: "drink", snapshot: wine, start: iso(80), duration: 20 },
    { type: "drink", snapshot: pint, start: iso(35), duration: 30 } ] },
];

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: "en-GB", timezoneId: "Europe/London" });
const page = await context.newPage();
await page.clock.setFixedTime(evening);
await page.emulateMedia({ reducedMotion: "reduce" });
await page.addInitScript((s) => {
  localStorage.setItem("oh.v1.disclaimer", "true");
  localStorage.setItem("oh.v1.profile", JSON.stringify({ sex: "Female", born: "1995-05-05", weight: 64, height: 168 }));
  localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
  localStorage.setItem("oh.v1.installHint", "dismissed");
}, sessions);
fs.mkdirSync(outDir, { recursive: true });

async function shoot(name, url, prepare = async () => {}) {
  await page.goto(origin + url);
  await page.getByText("Owl Hours").or(page.getByRole("heading")).first().waitFor({ timeout: 30_000 });
  await prepare();
  await page.waitForTimeout(1500);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: path.join(outDir, `${name}.png`) });
}
await shoot("tonight", "/", () => page.getByTestId("bac").waitFor());
await shoot("add-drink", "/#/add", async () => {
  await page.getByRole("searchbox").or(page.getByPlaceholder(/Search/)).first().fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).first().click();
});
await shoot("night", "/#/history/saturday");
await browser.close();
server.close();

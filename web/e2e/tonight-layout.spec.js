import { test, expect } from "@playwright/test";

test.use({ viewport: { width: 390, height: 844 } });

const profile = { sex: "Female", born: "1995-05-05", weight: 64, height: 168 };
const iso = (m) => new Date(Date.now() - m * 60_000).toISOString();
const pint = { catalogueId: "pale-lager", name: "Pale lager / Pilsner", volume: 500, abv: 5, kcalPerLitre: 404.15, components: [] };

async function seed(page, sessions) {
  await page.addInitScript(([p, s]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
    localStorage.setItem("oh.v1.installHint", "dismissed");
  }, [profile, sessions]);
}

const night = () => [{ id: "n1", startedAt: iso(120), intakes: [
  { type: "drink", snapshot: pint, start: iso(120), duration: 30 },
  { type: "drink", snapshot: pint, start: iso(60), duration: 30 },
  { type: "water", volume: 330, start: iso(30), duration: 5 } ] }];

async function bottom(page, id) { return (await page.getByTestId(id).boundingBox()).y + (await page.getByTestId(id).boundingBox()).height; }

for (const [name, sessions] of [["a night in progress", night()], ["no night yet", []]]) {
  test(`${name}: everything sits above the action bar without scrolling`, async ({ page }) => {
    await seed(page, sessions);
    await page.goto("/");
    await expect(page.getByTestId("bac")).toBeVisible();
    const bar = await page.getByTestId("action-bar").boundingBox();
    expect(bar.y + bar.height).toBeLessThanOrEqual(844);
    for (const id of ["sign", "chart-card"]) expect(await bottom(page, id), id).toBeLessThanOrEqual(bar.y);
    if (sessions.length) expect(await bottom(page, "chips")).toBeLessThanOrEqual(bar.y);
    else await expect(page.getByTestId("chips")).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
}

test("the sign panel holds Watt, the number, the trend and the state, side by side", async ({ page }) => {
  await seed(page, night());
  await page.goto("/");
  const sign = page.getByTestId("sign");
  const watt = await sign.getByTestId("watt").boundingBox();
  const bac = await sign.getByTestId("bac").boundingBox();
  expect(bac.x).toBeGreaterThan(watt.x + watt.width - 8); // number to the right of Watt
  expect(Math.abs(bac.y - watt.y)).toBeLessThan(watt.height); // on the same band
  await expect(sign.getByTestId("state")).toBeVisible();
  const box = await sign.boundingBox();
  expect(box.width).toBeGreaterThanOrEqual(390 - 2); // full width
});

test("the header's icon buttons are named and lead to their screens", async ({ page }) => {
  await seed(page, night());
  await page.goto("/");
  const nav = page.getByRole("navigation", { name: "Sections" });
  for (const [name, heading] of [["Log", "Log"], ["History", "History"], ["Settings", "Settings"]]) {
    await nav.getByRole("button", { name }).click();
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible();
    await page.goto("/");
  }
  await expect(nav.getByRole("button", { name: "About" })).toHaveCount(0);
});

test("chips carry their glowing dots; the action bar's buttons carry icons", async ({ page }) => {
  await seed(page, night());
  await page.goto("/");
  await expect(page.getByTestId("chips").locator(".oh-dot")).toHaveCount(2);
  await expect(page.getByTestId("action-bar").locator("svg.neon-icon")).toHaveCount(4);
});

test("the chart card has its title and legend above the curve", async ({ page }) => {
  await seed(page, night());
  await page.goto("/");
  const card = page.getByTestId("chart-card");
  await expect(card).toContainText("Your curve");
  for (const item of ["so far", "projected", "varies"]) await expect(card).toContainText(item);
  await expect(card.locator("svg")).toHaveCount(1);
});

test("a long state name stays inside the screen, swash included", async ({ page }) => {
  // one pint 45 minutes ago: warming up
  await seed(page, [{ id: "w1", startedAt: iso(45), intakes: [{ type: "drink", snapshot: pint, start: iso(45), duration: 15 }] }]);
  await page.goto("/");
  const state = page.getByTestId("state");
  await expect(state).toHaveText("Warming up");
  const box = await state.boundingBox();
  expect(box.x + box.width).toBeLessThanOrEqual(390);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("the action bar is four equal tiles; Same again wears the drinks' pink like the others wear theirs", async ({ page }) => {
  await seed(page, night());
  await page.goto("/");
  const tiles = page.getByTestId("action-bar").getByRole("button");
  await expect(tiles).toHaveCount(4);
  const boxes = await Promise.all([0, 1, 2, 3].map((i) => tiles.nth(i).boundingBox()));
  for (const b of boxes) {
    expect(Math.abs(b.width - boxes[0].width)).toBeLessThanOrEqual(1);
    expect(Math.abs(b.height - boxes[0].height)).toBeLessThanOrEqual(1);
  }
  const same = page.getByRole("button", { name: "Same again" });
  // Tailwind 4 writes translucent colours in oklab, so compare with a probe that has the pink-at-60 % border
  const pink60 = await page.evaluate(() => {
    const probe = document.createElement("div");
    probe.className = "border border-pink/60";
    document.body.append(probe);
    const c = getComputedStyle(probe).borderTopColor;
    probe.remove();
    return c;
  });
  expect(await same.evaluate((el) => getComputedStyle(el).borderTopColor)).toBe(pink60);
  expect(await same.evaluate((el) => getComputedStyle(el).color)).toContain("255, 63, 142");
});

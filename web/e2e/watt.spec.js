import { test, expect } from "@playwright/test";

const profile = { sex: "Male", born: "1996-01-01", weight: 80, height: 180 };
const vodka = { catalogueId: "vodka", name: "Vodka", volume: 1500, abv: 40, kcalPerLitre: 2210, components: [] };

async function onboarded(page, sessions) {
  await page.addInitScript(([p, s]) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
    if (s) localStorage.setItem("oh.v1.sessions", JSON.stringify(s));
  }, [profile, sessions]);
}

test("Watt is sober before the first drink", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  await expect(page.getByTestId("watt").getByRole("img", { name: "Watt the owl, Sober" })).toBeVisible();
});

test("far above 3 per mille Watt switches to the calm take-care state", async ({ page }) => {
  const at = new Date(Date.now() - 60 * 60_000).toISOString();
  await onboarded(page, [{ id: "night-x", startedAt: at, intakes: [{ type: "drink", snapshot: vodka, start: at, duration: 30 }] }]);
  await page.goto("/");
  await expect(page.getByTestId("watt").getByRole("img", { name: "Watt the owl, Take care" })).toBeVisible();
  await expect(page.getByText("Let's slow everything down.")).toBeVisible();
});

test("the owl is not redrawn on a clock tick, so its motion does not restart", async ({ page }) => {
  await page.clock.install();
  await onboarded(page);
  await page.goto("/");
  await page.locator("[data-testid=watt] svg").evaluate((el) => el.setAttribute("data-probe", "kept"));
  await page.clock.runFor(31_000);
  await page.waitForTimeout(300);
  await expect(page.locator("[data-testid=watt] svg[data-probe=kept]")).toHaveCount(1);
});

test("flicker stays within photosensitivity limits: ≤ 3 dips per second, never below 60 %", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  const report = await page.evaluate(() => {
    const frames = {};
    const durations = {};
    for (const sheet of document.styleSheets)
      for (const r of sheet.cssRules) {
        if (r instanceof CSSKeyframesRule && r.name.startsWith("watt-"))
          frames[r.name] = [...r.cssRules]
            .flatMap((k) => k.keyText.split(",").map((t) => ({ at: parseFloat(t), opacity: k.style.opacity })))
            .sort((a, b) => a.at - b.at);
        if (r instanceof CSSStyleRule && r.style.animationName && r.style.animationName.startsWith("watt-"))
          durations[r.style.animationName] = Math.min(durations[r.style.animationName] ?? Infinity, parseFloat(r.style.animationDuration));
      }
    return { frames, durations };
  });
  expect(Object.keys(report.frames).length).toBeGreaterThan(5);
  for (const [name, frames] of Object.entries(report.frames)) {
    if (name === "watt-rise") continue; // a slow fade-in/out of bubbles, not a flash
    const opacities = frames.filter((f) => f.opacity !== "").map((f) => parseFloat(f.opacity));
    for (const o of opacities) expect(o, name).toBeGreaterThanOrEqual(0.6);
    const dips = opacities.filter((o, i) => i > 0 && o < opacities[i - 1]).length;
    if (report.durations[name]) expect(dips / report.durations[name], name).toBeLessThanOrEqual(3);
  }
});

test("reduced motion stops every animation", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await onboarded(page);
  await page.goto("/");
  const names = await page.locator("[data-testid=watt] svg [class]").evaluateAll((els) =>
    els.map((e) => getComputedStyle(e).animationName));
  expect(names.length).toBeGreaterThan(0);
  expect(names.every((n) => n === "none")).toBe(true);
});

test("review: nested animations never compose below 60 % (a flickering segment inside the humming sign)", async ({ page }) => {
  await onboarded(page);
  await page.goto("/");
  const mins = await page.evaluate(() => {
    const out = {};
    for (const sheet of document.styleSheets)
      for (const r of sheet.cssRules)
        if (r instanceof CSSKeyframesRule && r.name.startsWith("watt-"))
          out[r.name] = Math.min(1, ...[...r.cssRules].map((k) => (k.style.opacity === "" ? 1 : parseFloat(k.style.opacity))));
    return out;
  });
  // whole-sign motions wrap the drawing; segment animations sit inside them
  const whole = Math.min(...["watt-hum", "watt-jitter", "watt-sway", "watt-sway2"].map((n) => mins[n] ?? 1));
  for (const n of ["watt-flick1", "watt-flick2", "watt-flick3", "watt-twinkle"])
    expect(mins[n] * whole, n).toBeGreaterThanOrEqual(0.6);
});

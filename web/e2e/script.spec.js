import { test, expect } from "@playwright/test";

const profile = { sex: "Female", born: "1995-05-05", weight: 64, height: 168 };

test("a script heading's swash stays inside its own box (Negroni's N)", async ({ page }) => {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
  await page.goto("/#/add");
  await page.getByPlaceholder(/Search/).fill("negroni");
  await page.getByRole("button", { name: /^Negroni/ }).first().click();
  const heading = page.getByRole("heading", { name: "Negroni" });
  await expect(heading).toBeVisible();
  const { overhang, padding } = await heading.evaluate(async (el) => {
    await document.fonts.ready;
    const cs = getComputedStyle(el);
    const ctx = document.createElement("canvas").getContext("2d");
    ctx.font = `${cs.fontSize} ${cs.fontFamily}`;
    return { overhang: ctx.measureText(el.textContent).actualBoundingBoxLeft, padding: parseFloat(cs.paddingLeft) };
  });
  expect(overhang).toBeGreaterThan(0); // the font really does overhang
  expect(padding).toBeGreaterThanOrEqual(overhang);
});

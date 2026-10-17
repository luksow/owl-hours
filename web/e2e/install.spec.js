import { test, expect } from "@playwright/test";

const profile = { sex: "Female", born: "1995-05-05", weight: 64, height: 168 };
const iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";

async function onboarded(page) {
  await page.addInitScript((p) => {
    localStorage.setItem("oh.v1.disclaimer", "true");
    localStorage.setItem("oh.v1.profile", JSON.stringify(p));
  }, profile);
}

/** What Chromium does once the app is installable; prompt() records that it was called. */
const offerInstall = (page) =>
  page.evaluate(() => {
    const e = new Event("beforeinstallprompt", { cancelable: true });
    e.prompt = async () => { window.__prompted = true; };
    e.userChoice = Promise.resolve({ outcome: "accepted", platform: "web" });
    window.dispatchEvent(e);
  });

const card = (page) => page.getByRole("region", { name: "Install Owl Hours" });

test.describe("Chromium", () => {
  test("no card until the browser offers to install; Install opens the browser's dialog", async ({ page }) => {
    await onboarded(page);
    await page.goto("/");
    await expect(page.getByTestId("bac")).toBeVisible();
    await expect(card(page)).toHaveCount(0);
    await offerInstall(page);
    await expect(card(page)).toBeVisible();
    await card(page).getByRole("button", { name: "Install" }).click();
    expect(await page.evaluate(() => window.__prompted)).toBe(true);
    await expect(card(page)).toHaveCount(0); // the event is spent
  });

  test("an offer made before the app is ready still shows once Tonight appears", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Before we start" })).toBeVisible();
    await offerInstall(page);
    // a reload would lose the event, so onboarding is finished through the form, as in e2e/onboarding.spec.js
    await page.getByLabel("I understand").check();
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByLabel("Female", { exact: true }).check();
    await page.getByLabel("Date of birth").fill("1995-05-05");
    await page.getByLabel("Weight (kg)").fill("64");
    await page.getByLabel("Height (cm)").fill("168");
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByText("Tonight", { exact: true })).toBeVisible();
    await expect(card(page)).toBeVisible();
  });

  test("installing from the browser menu hides the card at once", async ({ page }) => {
    await onboarded(page);
    await page.goto("/");
    await offerInstall(page);
    await expect(card(page)).toBeVisible();
    await page.evaluate(() => window.dispatchEvent(new Event("appinstalled")));
    await expect(card(page)).toHaveCount(0);
  });

  test("Not now hides the card for good; Settings still offers it", async ({ page }) => {
    await onboarded(page);
    await page.goto("/");
    await offerInstall(page);
    await card(page).getByRole("button", { name: "Not now" }).click();
    await expect(card(page)).toHaveCount(0);
    await page.reload();
    await offerInstall(page);
    await expect(page.getByTestId("bac")).toBeVisible();
    await expect(card(page)).toHaveCount(0);
    await page.getByRole("navigation", { name: "Sections" }).getByRole("button", { name: "Settings" }).click();
    await expect(page.getByRole("button", { name: "Install" })).toBeVisible();
  });

  test("with storage blocked, Not now still hides the card for this visit", async ({ page }) => {
    await onboarded(page);
    await page.addInitScript(() => {
      const set = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k, v) {
        if (k === "oh.v1.installHint") throw new DOMException("blocked", "QuotaExceededError");
        return set.call(this, k, v);
      };
    });
    await page.goto("/");
    await offerInstall(page);
    await card(page).getByRole("button", { name: "Not now" }).click();
    await expect(card(page)).toHaveCount(0);
  });
});

test.describe("iOS", () => {
  test.use({ userAgent: iphone });

  test("Safari users get the Share → Add to Home Screen hint, and Got it hides it for good", async ({ page }) => {
    await onboarded(page);
    await page.goto("/");
    await expect(card(page)).toContainText("Add to Home Screen");
    await card(page).getByRole("button", { name: "Got it" }).click();
    await expect(card(page)).toHaveCount(0);
    await page.reload();
    await expect(page.getByTestId("bac")).toBeVisible();
    await expect(card(page)).toHaveCount(0);
  });

  test("inside the installed app there is no hint", async ({ page }) => {
    await onboarded(page);
    await page.addInitScript(() => Object.defineProperty(navigator, "standalone", { value: true }));
    await page.goto("/");
    await expect(page.getByTestId("bac")).toBeVisible();
    await expect(card(page)).toHaveCount(0);
  });
});

import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  use: { baseURL: "http://localhost:4173", ...devices["Pixel 7"] },
  webServer: {
    command: "npm run build && npm run preview",
    url: "http://localhost:4173",
    timeout: 900_000,
    reuseExistingServer: !process.env.CI,
  },
});

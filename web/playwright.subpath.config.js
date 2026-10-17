import { defineConfig, devices } from "@playwright/test";

// Builds the app as GitHub Pages serves a project site (under /<repo>/) and smoke-tests it there.
export default defineConfig({
  testDir: "e2e-subpath",
  timeout: 60_000,
  use: { baseURL: "http://localhost:4174/owl-hours/", ...devices["Pixel 7"] },
  webServer: {
    command: "npx vite build --outDir dist-subpath && npx vite preview --outDir dist-subpath --port 4174 --strictPort",
    env: { OH_BASE: "/owl-hours/", OH_SITE_URL: "https://owlhours.example/owl-hours/" },
    url: "http://localhost:4174/owl-hours/",
    timeout: 900_000,
    reuseExistingServer: !process.env.CI,
  },
});

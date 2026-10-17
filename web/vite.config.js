import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import fs from "node:fs";
import path from "node:path";

const cataloguePath = path.resolve(import.meta.dirname, "../modules/catalogue/data/catalogue.json");

/** Serves the single source of truth for drink data in dev and emits it into production builds. */
function catalogueAsset() {
  return {
    name: "owlhours-catalogue",
    configureServer(server) {
      server.middlewares.use("/catalogue.json", (_req, res) => {
        res.setHeader("Content-Type", "application/json");
        res.end(fs.readFileSync(cataloguePath));
      });
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "catalogue.json", source: fs.readFileSync(cataloguePath) });
    },
  };
}

export default defineConfig({
  plugins: [
    scalaJSPlugin({ cwd: "..", projectID: "app" }),
    tailwindcss(),
    catalogueAsset(),
    VitePWA({
      registerType: "prompt",
      injectRegister: false,
      includeAssets: ["owl.svg", "icon.svg", "favicon.ico", "apple-touch-icon-180x180.png"],
      manifest: {
        name: "Owl Hours",
        short_name: "Owl Hours",
        description: "Know your curve before the night does. BAC estimates for a night out — never for driving decisions.",
        start_url: ".",
        display: "standalone",
        orientation: "portrait",
        background_color: "#120A0E",
        theme_color: "#120A0E",
        icons: [
          { src: "pwa-64x64.png", sizes: "64x64", type: "image/png" },
          { src: "pwa-192x192.png", sizes: "192x192", type: "image/png" },
          { src: "pwa-512x512.png", sizes: "512x512", type: "image/png" },
          { src: "maskable-icon-512x512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,json,woff,woff2,svg,png,ico,webmanifest}"],
        // Workbox silently skips files above 2 MiB; the Scala.js bundle is close to that, so leave headroom
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
    }),
  ],
});

import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import fs from "node:fs";
import path from "node:path";
import { splashScreens, splashFile, splashMedia } from "./brand/splash-sizes.js";

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

const base = process.env.OH_BASE ?? "/";
if (!base.startsWith("/") || !base.endsWith("/")) throw new Error(`OH_BASE must start and end with "/": ${base}`);
const site = process.env.OH_SITE_URL;
if (site !== undefined && !/^https?:\/\/.+\/$/.test(site)) throw new Error(`OH_SITE_URL must be an absolute URL ending with "/": ${site}`);

/** Absolute Open Graph URLs when the build knows where it will live (CI passes the Pages URL). */
function siteMeta() {
  return {
    name: "owlhours-site-meta",
    transformIndexHtml: () =>
      site === undefined ? [] : [
        { tag: "meta", attrs: { property: "og:url", content: site }, injectTo: "head" },
        { tag: "meta", attrs: { property: "og:image", content: new URL("og-image.png", site).href }, injectTo: "head" },
      ],
  };
}

/** iOS launch images (spec §10): one <link> per iPhone screen, from the same list the renderer uses. */
function iosSplash() {
  return {
    name: "owlhours-ios-splash",
    transformIndexHtml: () =>
      splashScreens.map((s) => ({
        tag: "link",
        attrs: { rel: "apple-touch-startup-image", href: splashFile(s), media: splashMedia(s) },
        injectTo: "head",
      })),
  };
}

export default defineConfig({
  base,
  plugins: [
    scalaJSPlugin({ cwd: "..", projectID: "app" }),
    tailwindcss(),
    catalogueAsset(),
    iosSplash(),
    siteMeta(),
    VitePWA({
      registerType: "prompt",
      injectRegister: false,
      includeAssets: ["owl.svg", "icon.svg", "favicon.ico", "apple-touch-icon-180x180.png"],
      // manifest icons would otherwise be precached whatever globIgnores says; the small ones still match globPatterns
      includeManifestIcons: false,
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
        // iOS fetches launch images while adding to the home screen and crawlers fetch the preview: not needed offline
        // the 512 px icons (one file twice, as "any" and as "maskable") are fetched by the browser when it installs
        globIgnores: ["**/splash/**", "**/og-image.png", "**/*512x512.png"],
        globPatterns: ["**/*.{js,css,html,json,woff,woff2,svg,png,ico,webmanifest}"],
        // Workbox silently skips files above 2 MiB; the Scala.js bundle is close to that, so leave headroom
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
    }),
  ],
});

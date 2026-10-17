# Owl Hours

Know your curve before the night does. An offline PWA that estimates blood alcohol over a night out,
using the physiological model by Podéus, Cedersund et al. (Linköping University).

Estimates only. Never use this app to decide whether you can drive.

## Develop

- `sbt test` — Scala tests on the JVM and Node (domain, model, catalogue, store, app logic)
- `sbt scalafmtAll` — formats the Scala code (braces only: `-no-indent` makes indentation-based blocks a compile error); CI runs `scalafmtCheckAll`
- `cd web && npm install` once, then:
  - `npm run dev` with `sbt ~app/fastLinkJS` in a second terminal — live development
  - `npm run build && npm run preview` — production build (fails if the main bundle is over 400 KB gzipped) on http://localhost:4173
  - `npm run test:e2e` — Playwright end-to-end tests (run `npx playwright install chromium` once)
  - `npm run test:e2e:subpath` — the same app built under `/owl-hours/`, as GitHub Pages serves it
  - `npm run assets` — re-render the icons, favicon, social preview and iOS launch images after changing `public/icon.svg`, `public/owl.svg` or `brand/`
  - `npm run lighthouse` — Lighthouse CI on `dist/` (needs Chrome: ``CHROME_PATH="$(node -e 'import("@playwright/test").then(m => console.log(m.chromium.executablePath()))')" npm run lighthouse``)
- `cd reference && uv run python run_scenarios.py` — regenerate golden curves from the authors' Python model

CI (`.github/workflows/ci.yml`) runs all of the above on pushes to `main`, on pull requests and on demand. Deploys to GitHub Pages run from `main` once the repository variable `OH_DEPLOY` is `true`.

## Your data

Everything stays in the browser on your device. Settings → Your data exports a backup file and imports one; "Delete all data" removes everything Owl Hours stored.

Licence: Apache-2.0. The ported model keeps its authors' MIT notice (`reference/LICENSE-alcohol-model.md`).

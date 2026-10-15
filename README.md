# Owl Hours

Know your curve before the night does. An offline PWA that estimates blood alcohol over a night out,
using the physiological model by Podéus, Cedersund et al. (Linköping University).

Estimates only. Never use this app to decide whether you can drive.

## Develop

- `sbt test` — Scala tests on the JVM and Node (domain, model, catalogue, store, app logic)
- `cd web && npm install` once, then:
  - `npm run dev` with `sbt ~app/fastLinkJS` in a second terminal — live development
  - `npm run build && npm run preview` — production build on http://localhost:4173
  - `npm run test:e2e` — Playwright end-to-end tests (run `npx playwright install chromium` once)
- `cd reference && uv run python run_scenarios.py` — regenerate golden curves from the authors' Python model

Licence: Apache-2.0. The ported model keeps its authors' MIT notice (`reference/LICENSE-alcohol-model.md`).

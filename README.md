# Owl Hours

Know your curve before the night does. An offline PWA that estimates blood alcohol over a night out,
using the physiological model by Podéus, Cedersund et al. (Linköping University).

Estimates only. Never use this app to decide whether you can drive.

## Develop

- `sbt test` — run all tests on the JVM and on Node (Scala.js)
- `cd reference && uv run python run_scenarios.py` — regenerate golden curves from the authors' Python model

Licence: Apache-2.0. The ported model keeps its authors' MIT notice (`reference/LICENSE-alcohol-model.md`).

# PcDiagnostics: notes for agents

- Never run `m`/`lunch`/soong here to test: `core/run_tests.sh`, `python3 -m unittest discover -s tests` and
  `tools/typecheck.sh` (README, "Tests"). Keep JVMs at -Xmx1.5g or less.
- TDD: test first in `core/tests/*Test.kt`, see it fail, then code. Checks, parsers and the runner go in
  `core/` (no Android); `src/` only reads the device and draws.
- The checks are named `*Test` too: `core/run_tests.sh` takes JUnit classes from `core/tests` only, and
  helpers inside test files must not end in `Test`.
- A new check: a `TestName`, its `FindingKey`s, a string per key in both languages, and the maps in
  `ui/Texts.kt`.
- Tables over if/else: `OVERALL_RULES`, `RateKind`, `judge(...)` rules, `Texts`, `CHIP_STYLES`, `LAYOUTS`.
  Names start with an English verb or prefix.
- UI text: pt-BR in `res/values-pt`, English in `res/values`; what happened and what to do, no em dash, no
  design explanations (`tests/test_strings.py`).
- Layout reference only: never copy code or assets from other diagnostics apps.
- Commits: no AI attribution lines.

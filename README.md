# Invest Plugin v1.3

Folia-compatible investment plugin for Paper 1.21+ by EliVB.

## Changes in v1.3

| | Change |
|---|---|
| 🐛 | **Sign editor bug fix** — opens reliably every single time. Root causes: temp sign was placed at player's feet (often non-air block) and `EntityScheduler` can be silently skipped by Folia. Fix: sign placed 3 blocks above player (always air) + `GlobalRegionScheduler` + one-tick retry guard. |
| ✨ | **Partial withdraw** — when auto-collect is **disabled**, clicking Collect Income prompts the player via sign to enter an amount (`1k`, `2.5m`, `all`, etc.) instead of collecting everything. |
| 📊 | **New placeholder** `%invest_income_per_sec_raw%` — plain decimal income/s for scoreboards. |

## Placeholders

| Placeholder | Output |
|---|---|
| `%invest_income_per_sec%` | `$1.5M` |
| `%invest_income_per_sec_raw%` | `1500000.0` ← **NEW** |
| `%invest_invested%` | `$250M` |
| `%invest_can_collect%` | `$4.2K` |

## Build

```bash
git clone https://github.com/YOUR_USER/InvestPlugin.git
cd InvestPlugin
mvn clean package
# output: target/Invest-1.3.jar
```

## Requirements
- Paper / Folia 1.21+ · Java 21
- Vault · PlaceholderAPI (optional)

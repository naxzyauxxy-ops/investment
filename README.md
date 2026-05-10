# Invest Plugin — v1.3

> Folia-compatible investment plugin for Paper 1.21+  
> by **EliVB** · [Discord](https://discord.gg/kEDAdrFRYa)

---

## Changes in v1.3

| | Change |
|---|---|
| 🐛 | **Sign editor bug fix** — sign input now opens reliably every time. Root cause: the original code placed a temp sign at the player's feet (could be non-air) and used `EntityScheduler` (can be silently skipped by Folia). Fix: temp sign placed 3 blocks above the player (always air), `GlobalRegionScheduler` used instead, one-tick retry guard added. |
| ✨ | **Partial withdraw** — when auto-collect is **disabled**, clicking *Collect Income* opens the sign editor so the player types how much they want: `1k`, `2.5m`, `all`, etc. Uses the same `economy.abbreviations` config as investing. |
| 📊 | **New placeholder** `%invest_income_per_sec_raw%` — raw decimal income/s, perfect for scoreboards. |

---

## Placeholders (PlaceholderAPI)

| Placeholder | Example output |
|---|---|
| `%invest_income_per_sec%` | `$1.5M` |
| `%invest_income_per_sec_raw%` | `1500000.0` ← **NEW** |
| `%invest_invested%` | `$250M` |
| `%invest_can_collect%` | `$4.2K` |

**Scoreboard example:**
```yaml
lines:
  - "&aIncome/s: &f%invest_income_per_sec%"
  - "&7Raw: %invest_income_per_sec_raw%"
```

---

## How the build works

This project ships the original `Invest-1.2.jar` inside `lib/`. Maven installs it into the local repo during the `initialize` phase, then compiles just the 4 patched/new source files against it. The shade plugin merges the original classes with the new ones into the final JAR — with the patched classes overriding the originals.

```bash
git clone https://github.com/YOUR_USER/InvestPlugin.git
cd InvestPlugin
mvn clean package
# → target/Invest-1.3.jar
```

---

## Requirements

- Paper / Folia **1.21+** · Java **21**
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) *(optional)*

---

## Project structure

```
InvestPlugin/
├── lib/
│   └── Invest-original.jar      ← v1.2 base (compile-time dep + shaded in)
├── pom.xml
├── .github/workflows/build.yml
└── src/main/
    ├── java/de/elivb/investment/
    │   ├── managers/
    │   │   ├── SignManager.java       ← PATCHED  (sign open bug fix)
    │   │   ├── PlaceholderManager.java ← PATCHED (new placeholder)
    │   │   ├── WithdrawManager.java   ← NEW     (partial withdraw)
    │   └── util/
    │       └── AmountParser.java      ← NEW     (abbreviation parser)
    └── resources/
        ├── plugin.yml  config.yml  lang.yml  mysql.yml
        └── gui/  main-gui.yml  confirm-*.yml
```

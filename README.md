# Invest Plugin

> A Folia-compatible investment plugin for Paper 1.21+  
> by **EliVB** · [Discord](https://discord.gg/kEDAdrFRYa)

---

## What's new in v1.3

| # | Change |
|---|--------|
| 🐛 | **Sign editor bug fix** — the sign input now opens reliably every time. The original code placed a temporary sign at the player's feet which could silently fail when the chunk wasn't ready or the block was already occupied. The fix places the sign 3 blocks above the player (always air), uses `GlobalRegionScheduler` instead of `EntityScheduler`, and adds a one-tick retry guard. |
| ✨ | **Partial withdraw** — when auto-collect is **disabled**, clicking *Collect Income* now opens the sign editor so the player can type exactly how much they want to withdraw (e.g. `1k`, `2.5m`, `all`). Abbreviations follow the same `economy.abbreviations` config as investing. |
| 📊 | **New placeholder** `%invest_income_per_sec_raw%` — returns the raw decimal value of income-per-second with no formatting, perfect for scoreboards and other plugins that need a plain number. |

---

## Placeholders (PlaceholderAPI)

| Placeholder | Description |
|-------------|-------------|
| `%invest_income_per_sec%` | Formatted income/s (e.g. `$1.5M`) |
| `%invest_income_per_sec_raw%` | **NEW** Raw decimal (e.g. `1500000.0`) — use in scoreboards |
| `%invest_invested%` | Total invested amount (formatted) |
| `%invest_can_collect%` | Pending collectible income (formatted) |

**Scoreboard example** (using a scoreboard plugin that supports PAPI):
```yaml
# Example with CMI or similar
lines:
  - "&aIncome/s: &f%invest_income_per_sec%"
  - "&7(raw: %invest_income_per_sec_raw%)"
```

---

## Requirements

- Paper / Folia **1.21+**
- Java **21**
- [Vault](https://www.spigotmc.org/resources/vault.34315/)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) *(optional)*

---

## Building from source

```bash
git clone https://github.com/YOUR_USER/InvestPlugin.git
cd InvestPlugin
mvn clean package
# Output → target/Invest-1.3-SNAPSHOT.jar
```

---

## Configuration

All config files are auto-generated on first launch inside your server's `plugins/Invest/` folder.

### config.yml — key options

```yaml
economy:
  abbreviations:
    enabled: true          # Accept 1k, 10m, 2.5b… everywhere (invest + withdraw)
    formats:
      - 'K'                # 1,000
      - 'M'                # 1,000,000
      - 'B'                # 1,000,000,000
      - 'T'                # 1,000,000,000,000
```

### Permissions

| Permission | Default | Description |
|---|---|---|
| `investment.use` | `true` | Use the `/invest` command |
| `investment.admin` | `op` | Reload, reset, set invest |
| `investment.autocollect` | `false` | Enable auto-collect toggle |
| `investment.vip` | `false` | Higher limits & income multiplier |

---

## Project structure

```
InvestPlugin/
├── pom.xml
├── README.md
├── .github/
│   └── workflows/
│       └── build.yml          ← GitHub Actions CI
└── src/main/
    ├── java/de/elivb/investment/
    │   ├── Investment.java          Main plugin class
    │   ├── HexColorCode.java        Colour utilities
    │   ├── managers/
    │   │   ├── SignManager.java      ← PATCHED (sign bug fix)
    │   │   ├── InvestmentManager.java
    │   │   ├── WithdrawManager.java  ← NEW  (partial withdraw)
    │   │   ├── PlaceholderManager.java ← PATCHED (new placeholder)
    │   │   ├── ConfigManager.java
    │   │   ├── DataManager.java
    │   │   ├── EconomyManager.java
    │   │   ├── SoundManager.java
    │   │   └── MySQLManager.java
    │   ├── gui/
    │   │   ├── MainGUI.java
    │   │   ├── ConfirmInvestGUI.java
    │   │   └── ConfirmDeleteGUI.java
    │   ├── listeners/
    │   │   └── GUIListener.java
    │   ├── commands/
    │   │   └── InvestCommand.java
    │   └── util/
    │       └── AmountParser.java     ← NEW  (abbreviation parsing)
    └── resources/
        ├── plugin.yml
        ├── config.yml
        ├── lang.yml
        ├── mysql.yml
        └── gui/
            ├── main-gui.yml
            ├── confirm-invest-gui.yml
            └── confirm-del-gui.yml
```

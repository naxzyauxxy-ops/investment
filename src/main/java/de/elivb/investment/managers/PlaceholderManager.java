package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * PlaceholderAPI expansion for the Invest plugin.
 *
 * Existing placeholders (unchanged behaviour):
 *   %invest_income_per_sec%     — formatted income per second  (e.g. "$1.5M")
 *   %invest_invested%           — total invested amount         (formatted)
 *
 * NEW placeholder added in v1.3:
 *   %invest_income_per_sec_raw% — raw decimal income per second (e.g. "1500000.0")
 *                                  No $ sign, no abbreviation. Use this on scoreboards
 *                                  or in other plugins that need a plain number.
 *
 * Example scoreboard line:
 *   "&aIncome/s: %invest_income_per_sec%"
 *   "&7Raw: %invest_income_per_sec_raw%"
 */
public class PlaceholderManager extends PlaceholderExpansion {

    private final Investment    plugin;
    private final DecimalFormat currencyFormat;

    // Sentinel values — match what the original code passes to obfuscated methods
    private static final int SENTINEL_CONFIG   = 1690924632;
    private static final int SENTINEL_DATA     = 1817610083;
    private static final int SENTINEL_INVEST   = 1343384936;
    private static final int SENTINEL_PLAYER   = 1604072148;
    private static final int SENTINEL_INVESTED = 1445869333;
    private static final int SENTINEL_IPS      = 765425982;
    private static final int SENTINEL_FORMAT   = 134911225;
    private static final int SENTINEL_CURRENCY = 1924836892;

    public PlaceholderManager(Investment plugin) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat(
                plugin.getConfigManager(SENTINEL_CONFIG).getCurrencyFormat(SENTINEL_CURRENCY));
    }

    @Override
    public @NotNull String getIdentifier() { return "invest"; }

    @Override
    public @NotNull String getAuthor() { return "EliVB"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        DataManager.PlayerData playerData =
                plugin.getDataManager(SENTINEL_DATA).getPlayerData(player, SENTINEL_PLAYER);

        switch (params.toLowerCase()) {

            case "income_per_sec":
                return formatWithAbbreviations(
                        plugin.getInvestmentManager(SENTINEL_INVEST)
                              .getIncomePerSecond(player, SENTINEL_IPS),
                        SENTINEL_FORMAT);

            // ── NEW ──────────────────────────────────────────────────────────
            // Raw decimal — perfect for scoreboards / other plugins
            case "income_per_sec_raw":
                return String.valueOf(
                        plugin.getInvestmentManager(SENTINEL_INVEST)
                              .getIncomePerSecond(player, SENTINEL_IPS));
            // ─────────────────────────────────────────────────────────────────

            case "invested":
                return formatWithAbbreviations(
                        playerData.getInvested(SENTINEL_INVESTED),
                        SENTINEL_FORMAT);

            case "can_collect":
            case "can_collect_formatted":
                return formatWithAbbreviations(
                        playerData.getCanCollect(1905859956),
                        SENTINEL_FORMAT);

            default:
                return null;
        }
    }

    public void registerExpansion(int n) {
        this.register();
    }

    // ── Formatting (mirrors original logic exactly) ───────────────────────────

    private String formatWithAbbreviations(double value, int sentinel) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) return "$" + currencyFormat.format(value / 1_000_000_000_000.0) + "T";
        if (abs >= 1_000_000_000.0)     return "$" + currencyFormat.format(value / 1_000_000_000.0)     + "B";
        if (abs >= 1_000_000.0)         return "$" + currencyFormat.format(value / 1_000_000.0)         + "M";
        if (abs >= 1_000.0)             return "$" + currencyFormat.format(value / 1_000.0)             + "K";
        return "$" + currencyFormat.format(value);
    }
}

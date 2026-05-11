package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * PlaceholderManager v1.3 — adds %invest_income_per_sec_raw% placeholder.
 *
 * Constructor and all method signatures exactly match the original class.
 *
 * NEW placeholder:
 *   %invest_income_per_sec_raw% — raw decimal income/s, no formatting.
 *   Perfect for scoreboards: %invest_income_per_sec_raw% gives "1500000.0"
 *   while %invest_income_per_sec% gives "$1.5M".
 */
public class PlaceholderManager extends PlaceholderExpansion {

    private final Investment    plugin;
    private final DecimalFormat currencyFormat;

    // ── Constructor — must match original: (Investment, int) ─────────────────
    public PlaceholderManager(final Investment plugin, final int n) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat(
                plugin.getConfigManager(1690924632).getCurrencyFormat(1924836892));
    }

    // ── PlaceholderExpansion overrides ────────────────────────────────────────

    @Override
    public @NotNull String getIdentifier() { return "invest"; }

    @Override
    public @NotNull String getAuthor() { return "EliVB"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(final Player player, @NotNull final String params) {
        if (player == null) return "";

        DataManager.PlayerData playerData =
                plugin.getDataManager(1817610083).getPlayerData(player, 1604072148);

        switch (params.toLowerCase()) {

            case "income_per_sec":
                return formatWithAbbreviations(
                        plugin.getInvestmentManager(1343384936)
                              .getIncomePerSecond(player, 765425982),
                        134911225);

            // ── NEW in v1.3 ───────────────────────────────────────────────────
            // Raw decimal, no $ / abbreviation — use this in scoreboard plugins
            case "income_per_sec_raw":
                return String.valueOf(
                        plugin.getInvestmentManager(1343384936)
                              .getIncomePerSecond(player, 765425982));
            // ─────────────────────────────────────────────────────────────────

            case "invested":
                return formatWithAbbreviations(playerData.getInvested(1445869333), 134911225);

            case "can_collect":
            case "can_collect_formatted":
                return formatWithAbbreviations(playerData.getCanCollect(1905859956), 134911225);

            default:
                return null;
        }
    }

    // ── Original methods (must keep same signatures) ──────────────────────────

    public void registerExpansion(final int n) {
        this.register();
    }

    public void unregisterExpansion(final int n) {
        this.unregister();
    }

    private String formatWithAbbreviations(final double value, final int n) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) return "$" + currencyFormat.format(value / 1_000_000_000_000.0) + "T";
        if (abs >= 1_000_000_000.0)     return "$" + currencyFormat.format(value / 1_000_000_000.0)     + "B";
        if (abs >= 1_000_000.0)         return "$" + currencyFormat.format(value / 1_000_000.0)         + "M";
        if (abs >= 1_000.0)             return "$" + currencyFormat.format(value / 1_000.0)             + "K";
        return "$" + currencyFormat.format(value);
    }
}

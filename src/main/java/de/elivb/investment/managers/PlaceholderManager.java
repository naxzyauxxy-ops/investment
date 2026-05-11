package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import de.elivb.investment.listeners.GUIListenerPatch;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * PlaceholderManager v1.3
 *
 * All signatures match original exactly.
 *
 * Also registers GUIListenerPatch from the constructor (called from
 * Investment.onEnable right after GUIListener) so the partial-withdraw
 * hook is active without needing to modify Investment.java.
 *
 * NEW: %invest_income_per_sec_raw% — plain decimal income/s, no formatting.
 */
public class PlaceholderManager extends PlaceholderExpansion {

    private final Investment    plugin;
    private final DecimalFormat currencyFormat;

    public PlaceholderManager(final Investment plugin, final int n) {
        this.plugin = plugin;
        this.currencyFormat = new DecimalFormat(
                plugin.getConfigManager(1690924632).getCurrencyFormat(1924836892));

        // Register the GUIListener patch so partial-withdraw works.
        // This is safe here — called from Investment.onEnable on the main thread,
        // right after GUIListener is registered.
        new GUIListenerPatch(plugin);
    }

    @Override public @NotNull String getIdentifier() { return "invest"; }
    @Override public @NotNull String getAuthor()     { return "EliVB"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onPlaceholderRequest(final Player player, @NotNull final String params) {
        if (player == null) return "";

        DataManager.PlayerData pd =
                plugin.getDataManager(1817610083).getPlayerData(player, 1604072148);

        switch (params.toLowerCase()) {
            case "income_per_sec":
                return fmt(plugin.getInvestmentManager(1343384936)
                               .getIncomePerSecond(player, 765425982));

            case "income_per_sec_raw":
                return String.valueOf(plugin.getInvestmentManager(1343384936)
                                           .getIncomePerSecond(player, 765425982));

            case "invested":
                return fmt(pd.getInvested(1445869333));

            case "can_collect":
            case "can_collect_formatted":
                return fmt(pd.getCanCollect(1905859956));

            default:
                return null;
        }
    }

    public void registerExpansion(final int n)   { this.register(); }
    public void unregisterExpansion(final int n) { this.unregister(); }

    private String formatWithAbbreviations(final double value, final int n) { return fmt(value); }

    private String fmt(final double value) {
        final double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) return "$" + currencyFormat.format(value / 1_000_000_000_000.0) + "T";
        if (abs >= 1_000_000_000.0)     return "$" + currencyFormat.format(value / 1_000_000_000.0)     + "B";
        if (abs >= 1_000_000.0)         return "$" + currencyFormat.format(value / 1_000_000.0)         + "M";
        if (abs >= 1_000.0)             return "$" + currencyFormat.format(value / 1_000.0)             + "K";
        return "$" + currencyFormat.format(value);
    }
}

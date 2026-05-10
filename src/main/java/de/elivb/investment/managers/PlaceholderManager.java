package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import de.elivb.investment.util.AmountParser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

/**
 * PlaceholderAPI expansion for the Invest plugin.
 *
 * Placeholders:
 *   %invest_income_per_sec%      — formatted income per second (e.g. "$1.5M")
 *   %invest_income_per_sec_raw%  — NEW: raw number (no $ sign, no abbreviation)
 *                                   useful for scoreboard math / other plugins
 *   %invest_invested%            — total amount currently invested (formatted)
 *   %invest_can_collect%         — total pending income (formatted)
 */
public class PlaceholderManager extends PlaceholderExpansion {

    private final Investment plugin;
    private final DecimalFormat currencyFormat;

    public PlaceholderManager(Investment plugin) {
        this.plugin = plugin;
        String pattern = plugin.getConfigManager().getCurrencyFormat(0);
        DecimalFormat fmt;
        try {
            fmt = new DecimalFormat(pattern);
        } catch (Exception e) {
            fmt = new DecimalFormat("#,##0.##");
        }
        this.currencyFormat = fmt;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "invest";
    }

    @Override
    public @NotNull String getAuthor() {
        return "EliVB";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return "";
        Player player = offlinePlayer.getPlayer();
        if (player == null) return "";

        String key = params.toLowerCase();

        switch (key) {
            case "income_per_sec": {
                double ips = plugin.getInvestmentManager().getIncomePerSecond(player);
                return formatWithAbbreviations(ips);
            }
            // NEW placeholder — raw decimal value, no formatting
            // Great for scoreboards: %invest_income_per_sec_raw%
            case "income_per_sec_raw": {
                double ips = plugin.getInvestmentManager().getIncomePerSecond(player);
                return String.valueOf(ips);
            }
            case "invested": {
                double invested = plugin.getDataManager().getPlayerData(player).getInvested();
                return formatWithAbbreviations(invested);
            }
            case "can_collect":
            case "can_collect_formatted": {
                double canCollect = plugin.getDataManager().getPlayerData(player).getCanCollect();
                return formatWithAbbreviations(canCollect);
            }
            default:
                return null;
        }
    }

    // -----------------------------------------------------------------------

    private String formatWithAbbreviations(double value) {
        return AmountParser.format(value, currencyFormat);
    }
}

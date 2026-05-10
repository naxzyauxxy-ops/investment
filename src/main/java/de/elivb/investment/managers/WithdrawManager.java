package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import de.elivb.investment.util.AmountParser;
import org.bukkit.entity.Player;

/**
 * Handles the "withdraw partial amount" flow added in v1.3.
 *
 * When auto-collect is DISABLED the player can click "Collect Income" and
 * instead of taking everything, they are prompted via the sign editor to enter
 * how much they want to withdraw. Abbreviations (1k, 5m, 2.5b…) are supported.
 *
 * The full-collect path (auto-collect ON) is unchanged.
 */
public class WithdrawManager {

    private final Investment plugin;

    public WithdrawManager(Investment plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player clicks "Collect Income" with auto-collect OFF.
     * Opens the sign editor so they can type a specific amount.
     */
    public void openWithdrawInput(Player player) {
        double canCollect = plugin.getDataManager().getPlayerData(player).getCanCollect();

        if (canCollect <= 0) {
            String msg = plugin.getConfigManager().getMessage("nothing-to-collect", 0);
            player.sendMessage(de.elivb.investment.HexColorCode.translateAllColorCodes(
                    (plugin.getConfigManager().isPrefixEnabled()
                            ? plugin.getConfigManager().getPrefix() : "") + msg, 0));
            plugin.getSoundManager().playErrorSound(player);
            return;
        }

        // Build hint lines for the sign
        String formatted = AmountParser.format(canCollect,
                plugin.getEconomyManager().getCurrencyFormat());
        String[] lines = {
                "",
                "↑ Amount ↑",
                "Max: " + formatted,
                ""
        };

        plugin.getSignManager().openSignEditor(player, lines, (p, input) -> {
            handleWithdrawInput(p, input, canCollect);
        });
    }

    private void handleWithdrawInput(Player player, String input, double maxCanCollect) {
        if (input == null || input.isBlank()) {
            // Cancelled — reopen GUI
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    t -> plugin.getMainGUI().openInventory(player));
            return;
        }

        boolean abbrevEnabled = plugin.getConfigManager().isAbbreviationsEnabled(0);
        Double parsed = AmountParser.parse(input,
                plugin.getConfigManager().getAbbreviationFormats(), abbrevEnabled);

        if (parsed == null) {
            sendError(player, "invalid-amount");
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    t -> plugin.getMainGUI().openInventory(player));
            return;
        }

        // "all" sentinel
        double amount = (parsed == AmountParser.ALL) ? maxCanCollect : parsed;

        // Re-fetch in case it changed
        double currentCanCollect = plugin.getDataManager().getPlayerData(player).getCanCollect();

        if (amount <= 0) {
            sendError(player, "invalid-amount");
            plugin.getServer().getGlobalRegionScheduler().run(plugin,
                    t -> plugin.getMainGUI().openInventory(player));
            return;
        }

        if (amount > currentCanCollect) {
            amount = currentCanCollect; // clamp to available
        }

        final double finalAmount = amount;

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline()) return;

            // Deposit the requested portion
            plugin.getEconomyManager().depositMoney(player, finalAmount);

            // Deduct from pending
            double remaining = currentCanCollect - finalAmount;
            plugin.getDataManager().getPlayerData(player).setCanCollect(remaining);
            plugin.getDataManager().savePlayer(player);

            // Feedback
            plugin.getSoundManager().playCollectSound(player);
            plugin.getInvestmentManager().sendIncomeMessage(player, finalAmount);

            // Reopen GUI
            plugin.getMainGUI().openInventory(player);
        });
    }

    private void sendError(Player player, String msgKey) {
        String msg = plugin.getConfigManager().getMessage(msgKey, 0);
        if (msg == null) msg = "Error!";
        player.sendMessage(de.elivb.investment.HexColorCode.translateAllColorCodes(
                (plugin.getConfigManager().isPrefixEnabled()
                        ? plugin.getConfigManager().getPrefix() : "") + msg, 0));
    }
}

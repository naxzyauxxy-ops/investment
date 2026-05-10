package de.elivb.investment.managers;

import de.elivb.investment.HexColorCode;
import de.elivb.investment.Investment;
import de.elivb.investment.util.AmountParser;
import org.bukkit.entity.Player;

/**
 * WithdrawManager — added in v1.3.
 *
 * When auto-collect is DISABLED, clicking "Collect Income" now opens the sign
 * editor so the player can type exactly how much they want to withdraw.
 * Abbreviations (1k, 2.5m, 500b, "all") follow the same config as investing.
 *
 * The full-collect path (auto-collect ON) is completely unchanged.
 *
 * This class is self-contained: it only uses public APIs from the existing
 * managers so it does not require source changes to any other class.
 */
public class WithdrawManager {

    // Sentinel ints matched from the original obfuscated call-sites
    private static final int S_CONFIG      = 1690924632;
    private static final int S_DATA        = 1817610083;
    private static final int S_ECONOMY     = 6722847;
    private static final int S_SOUND       = 1185677687;
    private static final int S_INVEST_MGR  = 1343384936;
    private static final int S_MAIN_GUI    = 837914895;
    private static final int S_SIGN_MGR    = 1837252308;
    private static final int S_PLAYER_DATA = 1604072148;
    private static final int S_CAN_COLLECT = 1905859956;
    private static final int S_SAVE_PLAYER = 84869746;
    private static final int S_DEPOSIT     = 1046533418;
    private static final int S_FORMAT_MONEY = 1866020738;
    private static final int S_OPEN_INV    = 187107641;
    private static final int S_MSG         = 1774950920;
    private static final int S_COLOR       = 1545345783;
    private static final int S_PLAY_SOUND  = 671416715;

    private final Investment plugin;

    public WithdrawManager(Investment plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player clicks "Collect Income" with auto-collect OFF.
     * Opens the sign editor prompting for a specific amount.
     */
    public void openWithdrawInput(Player player) {
        double canCollect = plugin.getDataManager(S_DATA)
                                  .getPlayerData(player, S_PLAYER_DATA)
                                  .getCanCollect(S_CAN_COLLECT);

        if (canCollect <= 0) {
            String msg = plugin.getConfigManager(S_CONFIG)
                               .getMessage("nothing-to-collect", S_MSG);
            String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                            ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
            player.sendMessage(HexColorCode.translateAllColorCodes(prefix + msg, S_COLOR));
            return;
        }

        String maxFormatted = plugin.getEconomyManager(S_ECONOMY)
                                    .formatMoney(canCollect, S_FORMAT_MONEY);

        // Sign lines shown to the player
        String[] lines = {
                "",
                "\u2191\u2191 Amount \u2191\u2191",  // ↑↑ Amount ↑↑
                "Max: " + maxFormatted,
                ""
        };

        plugin.getSignManager(S_SIGN_MGR).openSignEditor(player, lines,
                (p, input) -> handleInput(p, input, canCollect));
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void handleInput(Player player, String input, double snapshotMax) {
        // Empty / cancelled → reopen GUI
        if (input == null || input.isBlank()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t ->
                    plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV));
            return;
        }

        boolean abbrevEnabled = plugin.getConfigManager(S_CONFIG)
                                      .isAbbreviationsEnabled(S_CONFIG);

        Double parsed = AmountParser.parse(input,
                plugin.getConfigManager(S_CONFIG).getAbbreviationFormats(),
                abbrevEnabled);

        if (parsed == null) {
            sendMessage(player, "invalid-amount");
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t ->
                    plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV));
            return;
        }

        // Re-fetch canCollect in case it changed between sign-open and sign-close
        double currentCanCollect = plugin.getDataManager(S_DATA)
                                         .getPlayerData(player, S_PLAYER_DATA)
                                         .getCanCollect(S_CAN_COLLECT);

        // "all" sentinel → take everything
        double amount = (parsed == AmountParser.ALL) ? currentCanCollect : parsed;

        if (amount <= 0) {
            sendMessage(player, "invalid-amount");
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t ->
                    plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV));
            return;
        }

        // Clamp to available
        if (amount > currentCanCollect) amount = currentCanCollect;

        final double finalAmount = amount;

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline()) return;

            DataManager.PlayerData pd = plugin.getDataManager(S_DATA)
                                              .getPlayerData(player, S_PLAYER_DATA);

            // Deposit the requested portion
            plugin.getEconomyManager(S_ECONOMY).depositMoney(player, finalAmount, S_DEPOSIT);

            // Deduct from pending
            double remaining = Math.max(0, pd.getCanCollect(S_CAN_COLLECT) - finalAmount);
            pd.setCanCollect(remaining, 687395578);

            // Persist
            plugin.getDataManager(S_DATA).savePlayer(player, S_SAVE_PLAYER);

            // Action-bar income message (mirrors original sendIncomeMessage style)
            String formatted = plugin.getEconomyManager(S_ECONOMY)
                                     .formatMoney(finalAmount, S_FORMAT_MONEY);
            String actionBarText = HexColorCode.translateAllColorCodes("&#7CFC00&l+" + formatted, S_COLOR);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(actionBarText));

            // Reopen GUI
            plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV);
        });
    }

    private void sendMessage(Player player, String key) {
        String msg    = plugin.getConfigManager(S_CONFIG).getMessage(key, S_MSG);
        String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                        ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
        if (msg == null) msg = key;
        player.sendMessage(HexColorCode.translateAllColorCodes(prefix + msg, S_COLOR));
    }
}

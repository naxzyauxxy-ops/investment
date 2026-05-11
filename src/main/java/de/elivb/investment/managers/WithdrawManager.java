package de.elivb.investment.managers;

import de.elivb.investment.HexColorCode;
import de.elivb.investment.Investment;
import de.elivb.investment.util.AmountParser;
import org.bukkit.entity.Player;

/**
 * WithdrawManager v1.3 — partial income withdrawal via sign input.
 *
 * When auto-collect is DISABLED, the collect button opens the sign editor
 * so the player can type how much to withdraw: 1k, 2.5m, all, etc.
 */
public class WithdrawManager {

    private static final int S_CONFIG      = 1690924632;
    private static final int S_DATA        = 1817610083;
    private static final int S_ECONOMY     = 6722847;
    private static final int S_MAIN_GUI    = 837914895;
    private static final int S_SIGN_MGR    = 1837252308;
    private static final int S_PLAYER_DATA = 1604072148;
    private static final int S_CAN_COLLECT = 1905859956;
    private static final int S_SET_COLLECT = 687395578;
    private static final int S_SAVE_PLAYER = 84869746;
    private static final int S_DEPOSIT     = 1046533418;
    private static final int S_FORMAT      = 1866020738;
    private static final int S_OPEN_INV    = 187107641;
    private static final int S_MSG         = 1774950920;
    private static final int S_COLOR       = 1545345783;

    private final Investment plugin;

    public WithdrawManager(final Investment plugin) {
        this.plugin = plugin;
    }

    /** Open the partial-withdraw sign input for a player. */
    public void openWithdrawInput(final Player player) {
        final double canCollect = plugin.getDataManager(S_DATA)
                                        .getPlayerData(player, S_PLAYER_DATA)
                                        .getCanCollect(S_CAN_COLLECT);

        if (canCollect <= 0) {
            sendMsg(player, "nothing-to-collect");
            return;
        }

        final String maxFmt = plugin.getEconomyManager(S_ECONOMY)
                                    .formatMoney(canCollect, S_FORMAT);

        // Use (Player, String, BiConsumer) overload — shows hint on line 0
        plugin.getSignManager(S_SIGN_MGR)
              .openSignEditor(player, "Max: " + maxFmt,
                      (p, input) -> handleInput(p, input, canCollect));
    }

    private void handleInput(final Player player, final String input,
                             final double snapshotMax) {
        if (input == null || input.isBlank()) {
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }

        final boolean abbrev = plugin.getConfigManager(S_CONFIG)
                                     .isAbbreviationsEnabled(S_CONFIG);
        final Double parsed = AmountParser.parse(input,
                plugin.getConfigManager(S_CONFIG).getAbbreviationFormats(), abbrev);

        if (parsed == null) {
            sendMsg(player, "invalid-amount");
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }

        // Re-fetch in case income ticked while sign was open
        final double current = plugin.getDataManager(S_DATA)
                                     .getPlayerData(player, S_PLAYER_DATA)
                                     .getCanCollect(S_CAN_COLLECT);

        double amount = (parsed == AmountParser.ALL) ? current : parsed;
        if (amount <= 0) {
            sendMsg(player, "invalid-amount");
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }
        if (amount > current) amount = current;

        final double finalAmount = amount;
        schedule(player, () -> {
            if (!player.isOnline()) return;

            final DataManager.PlayerData pd = plugin.getDataManager(S_DATA)
                                                    .getPlayerData(player, S_PLAYER_DATA);

            plugin.getEconomyManager(S_ECONOMY).depositMoney(player, finalAmount, S_DEPOSIT);

            final double remaining = Math.max(0, pd.getCanCollect(S_CAN_COLLECT) - finalAmount);
            pd.setCanCollect(remaining, S_SET_COLLECT);
            plugin.getDataManager(S_DATA).savePlayer(player, S_SAVE_PLAYER);

            final String formatted = plugin.getEconomyManager(S_ECONOMY)
                                           .formatMoney(finalAmount, S_FORMAT);
            final String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                                  ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
            final String collectMsg = plugin.getConfigManager(S_CONFIG)
                                           .getMessage("collect-income", S_MSG);
            if (collectMsg != null && !collectMsg.isEmpty()) {
                player.sendMessage(HexColorCode.translateAllColorCodes(
                        prefix + collectMsg.replace("{amount}", formatted), S_COLOR));
            }

            plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV);
        });
    }

    private void sendMsg(final Player player, final String key) {
        String msg    = plugin.getConfigManager(S_CONFIG).getMessage(key, S_MSG);
        final String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                              ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
        if (msg == null) msg = key;
        player.sendMessage(HexColorCode.translateAllColorCodes(prefix + msg, S_COLOR));
    }

    private void schedule(final Player player, final Runnable task) {
        player.getScheduler().run(plugin, t -> task.run(), null);
    }
}

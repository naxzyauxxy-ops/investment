package de.elivb.investment.managers;

import de.elivb.investment.HexColorCode;
import de.elivb.investment.Investment;
import de.elivb.investment.util.AmountParser;
import org.bukkit.entity.Player;

/**
 * WithdrawManager v1.3 — partial income withdrawal.
 *
 * When auto-collect is DISABLED, clicking "Collect Income" opens the sign editor
 * so the player can type exactly how much to withdraw: 1k, 2.5m, all, etc.
 * Abbreviations follow the same economy.abbreviations config as investing.
 */
public class WithdrawManager {

    // Sentinel ints taken from original call-sites in decompiled bytecode
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
    private static final int S_CURRENCY    = 1924836892;

    private final Investment plugin;

    public WithdrawManager(Investment plugin) {
        this.plugin = plugin;
    }

    /** Call this when player clicks Collect Income with auto-collect OFF. */
    public void openWithdrawInput(Player player) {
        double canCollect = plugin.getDataManager(S_DATA)
                                  .getPlayerData(player, S_PLAYER_DATA)
                                  .getCanCollect(S_CAN_COLLECT);

        if (canCollect <= 0) {
            sendMsg(player, "nothing-to-collect");
            return;
        }

        String maxFmt = plugin.getEconomyManager(S_ECONOMY)
                              .formatMoney(canCollect, S_FORMAT);

        // Build hint lines — use (Player, Player, String[], BiConsumer) overload
        String[] lines = { "", "\u2191 Amount \u2191", "Max: " + maxFmt, "" };

        // openSignEditor(Player p0, Player p2, String[] p3, BiConsumer p4)
        plugin.getSignManager(S_SIGN_MGR)
              .openSignEditor(player, player, lines,
                      (p, input) -> handleInput(p, input, canCollect));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void handleInput(Player player, String input, double snapshotMax) {
        if (input == null || input.isBlank()) {
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }

        boolean abbrev = plugin.getConfigManager(S_CONFIG).isAbbreviationsEnabled(S_CONFIG);
        Double parsed  = AmountParser.parse(input,
                plugin.getConfigManager(S_CONFIG).getAbbreviationFormats(), abbrev);

        if (parsed == null) {
            sendMsg(player, "invalid-amount");
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }

        // Re-fetch in case it changed while sign was open
        double current = plugin.getDataManager(S_DATA)
                               .getPlayerData(player, S_PLAYER_DATA)
                               .getCanCollect(S_CAN_COLLECT);

        double amount = (parsed == AmountParser.ALL) ? current : parsed;
        if (amount <= 0) {
            sendMsg(player, "invalid-amount");
            schedule(player, () -> plugin.getMainGUI(S_MAIN_GUI)
                                        .openInventory(player, S_OPEN_INV));
            return;
        }
        if (amount > current) amount = current; // clamp

        final double finalAmount = amount;

        schedule(player, () -> {
            if (!player.isOnline()) return;

            DataManager.PlayerData pd = plugin.getDataManager(S_DATA)
                                              .getPlayerData(player, S_PLAYER_DATA);

            plugin.getEconomyManager(S_ECONOMY).depositMoney(player, finalAmount, S_DEPOSIT);

            double remaining = Math.max(0, pd.getCanCollect(S_CAN_COLLECT) - finalAmount);
            pd.setCanCollect(remaining, S_SET_COLLECT);
            plugin.getDataManager(S_DATA).savePlayer(player, S_SAVE_PLAYER);

            String formatted = plugin.getEconomyManager(S_ECONOMY)
                                     .formatMoney(finalAmount, S_FORMAT);
            String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                            ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
            String collectMsg = plugin.getConfigManager(S_CONFIG)
                                      .getMessage("collect-income", S_MSG);
            if (collectMsg != null) {
                player.sendMessage(HexColorCode.translateAllColorCodes(
                        prefix + collectMsg.replace("{amount}", formatted), S_COLOR));
            }

            plugin.getMainGUI(S_MAIN_GUI).openInventory(player, S_OPEN_INV);
        });
    }

    private void sendMsg(Player player, String key) {
        String msg = plugin.getConfigManager(S_CONFIG).getMessage(key, S_MSG);
        String prefix = plugin.getConfigManager(S_CONFIG).isPrefixEnabled()
                        ? plugin.getConfigManager(S_CONFIG).getPrefix() : "";
        if (msg == null) msg = key;
        player.sendMessage(HexColorCode.translateAllColorCodes(prefix + msg, S_COLOR));
    }

    private void schedule(Player player, Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
    }
}

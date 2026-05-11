package de.elivb.investment.listeners;

import de.elivb.investment.Investment;
import de.elivb.investment.managers.DataManager;
import de.elivb.investment.managers.WithdrawManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * GUIListenerPatch — intercepts the collect-income slot BEFORE the original
 * GUIListener so we can open the partial-withdraw sign input when auto-collect is OFF.
 *
 * Priority HIGH fires before the original NORMAL handler.
 * We cancel the event to prevent the original from calling collectIncome().
 */
public class GUIListenerPatch implements Listener {

    private static final int S_CONFIG      = 1690924632;
    private static final int S_DATA        = 1817610083;
    private static final int S_PLAYER_DATA = 1604072148;
    private static final int S_AUTO_COLL   = 1639196651;
    private static final int S_COLOR       = 1545345783;
    // main-gui.yml: collect-income.slot = 15
    private static final int COLLECT_SLOT  = 15;

    private final Investment      plugin;
    private final WithdrawManager withdrawManager;

    public GUIListenerPatch(final Investment plugin) {
        this.plugin          = plugin;
        this.withdrawManager = new WithdrawManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCollectClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType().isAir()) return;
        if (event.getSlot() != COLLECT_SLOT) return;

        // Make sure we're in the main investment GUI by checking the title
        final String title = event.getView().getTitle();
        final String guiTitle = de.elivb.investment.HexColorCode.translateAllColorCodes(
                plugin.getConfigManager(S_CONFIG)
                      .getMainGuiConfig(989582897)
                      .getString("gui-settings.titel", ""), S_COLOR);
        if (!title.equals(guiTitle)) return;

        // Only intercept when auto-collect is OFF
        final DataManager.PlayerData pd = plugin.getDataManager(S_DATA)
                                                .getPlayerData(player, S_PLAYER_DATA);
        if (pd.isAutoCollect(S_AUTO_COLL)) return;

        // Cancel so original GUIListener.collectIncome() never fires
        event.setCancelled(true);

        // Open partial withdraw on the player's scheduler thread
        player.getScheduler().run(plugin, t ->
                withdrawManager.openWithdrawInput(player), null);
    }
}

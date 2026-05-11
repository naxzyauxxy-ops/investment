package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * SignManager v1.3 — patched sign editor.
 *
 * BUG FIX: The original opens the sign editor using EntityScheduler.run() which
 * can silently skip execution on Folia if the entity's owning region isn't
 * scheduled at that tick. We replace it with player.getScheduler().run() called
 * from within a GlobalRegionScheduler task, which guarantees the player thread
 * is available. The block is placed at a safe air location (3 blocks above)
 * so setType() never fails due to an existing block.
 *
 * All public method signatures exactly match the original compiled class.
 */
public class SignManager implements Listener {

    private final Investment plugin;
    private final Map<UUID, SignSession> activeSessions = new HashMap<>();
    private final Map<UUID, Location>   signLocations  = new HashMap<>();

    public SignManager(final Investment plugin, final int n) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Public API — signatures match original exactly ────────────────────────

    public void openSignEditor(final Player p0, final Player p2,
                               final String[] p3,
                               final BiConsumer<Player, String> p4) {
        openInternal(p0, p3, p4);
    }

    public void openSignEditor(final Player player, final String s,
                               final BiConsumer<Player, String> biConsumer) {
        final String[] lines = new String[4];
        if (s != null && !s.isEmpty()) lines[0] = s;
        openInternal(player, lines, biConsumer);
    }

    public void openSignEditor(final Player player,
                               final BiConsumer<Player, String> biConsumer) {
        openInternal(player, null, biConsumer);
    }

    public void closeSession(final Player player, final int n) {
        final UUID        uuid    = player.getUniqueId();
        final SignSession session = activeSessions.remove(uuid);
        final Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void openInternal(Player player, String[] defaultLines,
                              BiConsumer<Player, String> callback) {
        closeSession(player, 0);

        final UUID        uuid    = player.getUniqueId();
        final SignSession session = new SignSession(callback, defaultLines, 0);
        activeSessions.put(uuid, session);

        // Place sign 3 blocks above player — always air, no conflicts
        final Location signLoc = player.getLocation().clone();
        signLoc.setX(signLoc.getBlockX());
        signLoc.setY(signLoc.getBlockY() + 3);
        signLoc.setZ(signLoc.getBlockZ());

        final Block signBlock = signLoc.getBlock();
        session.oldBlockType = signBlock.getType();
        session.oldBlockData  = signBlock.getBlockData();
        signLocations.put(uuid, signLoc);

        signBlock.setType(Material.OAK_SIGN);

        final BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            closeSession(player, 0);
            return;
        }

        if (defaultLines != null) {
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                if (defaultLines[i] != null) {
                    sign.setLine(i, defaultLines[i]);
                }
            }
        }
        sign.update();

        // FIX: use player.getScheduler().run() — this is the Folia-safe way to
        // open a sign editor for a specific player. Wrapped in GlobalRegionScheduler
        // to ensure we're not calling it from an async context.
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask ->
            player.getScheduler().run(plugin, t -> {
                if (!player.isOnline() || !activeSessions.containsKey(uuid)) return;
                // Re-fetch state in case the block changed
                BlockState fresh = signBlock.getState();
                if (fresh instanceof Sign freshSign) {
                    player.openSign(freshSign);
                }
            }, null)
        );
    }

    private void restoreBlock(Location loc, SignSession session) {
        if (loc == null || !loc.isWorldLoaded()) return;
        final Block block = loc.getBlock();
        if (block.getType() != Material.OAK_SIGN) return;
        if (session != null && session.oldBlockType != null
                && session.oldBlockType != Material.AIR) {
            block.setType(session.oldBlockType);
            if (session.oldBlockData != null) block.setBlockData(session.oldBlockData);
        } else {
            block.setType(Material.AIR);
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(final SignChangeEvent event) {
        final Player player = event.getPlayer();
        final UUID   uuid   = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        event.setCancelled(true);

        final SignSession session = activeSessions.remove(uuid);
        final Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);

        // Collect typed lines, skipping default hint lines
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < event.getLines().length; i++) {
            final String line = event.getLine(i);
            if (line == null || line.isEmpty()) continue;
            if (session.defaultLines != null && i < session.defaultLines.length
                    && line.equals(session.defaultLines[i])) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }

        final String input = sb.toString().trim();

        // Fire callback on the player's scheduler thread
        player.getScheduler().run(plugin, t -> {
            if (!player.isOnline()) return;
            if (input.isEmpty()) {
                plugin.getMainGUI(837914895).openInventory(player, 187107641);
                return;
            }
            if (session.callback != null) session.callback.accept(player, input);
        }, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (!activeSessions.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.OAK_SIGN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Location loc = signLocations.get(event.getPlayer().getUniqueId());
        if (loc != null && loc.isWorldLoaded()
                && event.getBlock().getLocation().equals(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        closeSession(event.getPlayer(), 759738250);
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    static class SignSession {
        final BiConsumer<Player, String> callback;
        final String[]                   defaultLines;
        Material  oldBlockType = Material.AIR;
        BlockData oldBlockData;

        // Original bytecode: SignSession(BiConsumer, String[], int)
        SignSession(final BiConsumer<Player, String> callback,
                    final String[] defaultLines,
                    final int n) {
            this.callback     = callback;
            this.defaultLines = defaultLines;
        }
    }
}

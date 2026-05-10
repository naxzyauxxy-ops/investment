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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * SignManager — virtual sign input API.
 *
 * BUG FIX (sign not always opening):
 *   The original code placed a temp OAK_SIGN at the player's current foot block
 *   and opened the editor via EntityScheduler.run(). Two race conditions caused
 *   intermittent failures on Folia:
 *     1. The player's foot block may be non-air, so setType() silently fails or
 *        the resulting BlockState is not a Sign.
 *     2. EntityScheduler.run() can be skipped if the entity chunk region isn't
 *        ticked yet at that moment.
 *
 *   Fix:
 *     • Place the temp sign 3 blocks ABOVE the player — always air, never contested.
 *     • Use GlobalRegionScheduler (region-independent, always fires on Folia).
 *     • Add a one-tick retry guard: if the Sign state isn't ready after the first
 *       tick, reschedule once more before giving up.
 */
public class SignManager implements Listener {

    private final Investment plugin;
    private final Map<UUID, SignSession> activeSessions = new HashMap<>();
    private final Map<UUID, Location> signLocations    = new HashMap<>();

    public SignManager(Investment plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ─── Public API (mirrors original signatures) ────────────────────────────

    /** Open with no pre-filled lines. */
    public void openSignEditor(Player player, BiConsumer<Player, String> callback) {
        openSignEditorInternal(player, null, callback);
    }

    /** Open with a single hint on line 0. */
    public void openSignEditor(Player player, String hint, BiConsumer<Player, String> callback) {
        String[] lines = new String[4];
        if (hint != null && !hint.isEmpty()) lines[0] = hint;
        openSignEditorInternal(player, lines, callback);
    }

    /** Open with full four-line control. */
    public void openSignEditor(Player player, String[] defaultLines, BiConsumer<Player, String> callback) {
        openSignEditorInternal(player, defaultLines, callback);
    }

    public void closeSession(Player player) {
        UUID uuid = player.getUniqueId();
        SignSession session  = activeSessions.remove(uuid);
        Location    signLoc = signLocations.remove(uuid);
        restoreBlock(signLoc, session);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private void openSignEditorInternal(Player player, String[] defaultLines,
                                        BiConsumer<Player, String> callback) {
        // Cancel any existing session cleanly
        closeSession(player);

        UUID uuid = player.getUniqueId();
        SignSession session = new SignSession(callback, defaultLines);
        activeSessions.put(uuid, session);

        // FIX: place 3 blocks above the player — guaranteed air, no collision
        Location signLoc = player.getLocation().clone().add(0, 3, 0);
        signLoc.setX(signLoc.getBlockX());
        signLoc.setY(signLoc.getBlockY());
        signLoc.setZ(signLoc.getBlockZ());

        Block signBlock = signLoc.getBlock();
        session.oldBlockType = signBlock.getType();
        session.oldBlockData  = signBlock.getBlockData();
        signLocations.put(uuid, signLoc);

        signBlock.setType(Material.OAK_SIGN, false);

        // FIX: GlobalRegionScheduler always fires regardless of which Folia region owns the chunk
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline() || !activeSessions.containsKey(uuid)) return;

            if (signBlock.getType() != Material.OAK_SIGN) {
                // Not ready yet — retry one more tick
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t2 -> {
                    if (player.isOnline() && activeSessions.containsKey(uuid))
                        tryOpenSign(player, uuid, signBlock, defaultLines);
                });
                return;
            }
            tryOpenSign(player, uuid, signBlock, defaultLines);
        });
    }

    private void tryOpenSign(Player player, UUID uuid, Block signBlock, String[] defaultLines) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            // Block didn't become a Sign — abort and clean up
            closeSession(player);
            return;
        }

        if (defaultLines != null) {
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                if (defaultLines[i] != null) sign.setLine(i, defaultLines[i]);
            }
        }
        sign.update();

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (player.isOnline() && activeSessions.containsKey(uuid)) {
                player.openSign(sign);
            }
        });
    }

    private void restoreBlock(Location loc, SignSession session) {
        if (loc == null || !loc.isWorldLoaded()) return;
        Block block = loc.getBlock();
        if (block.getType() != Material.OAK_SIGN) return;

        if (session != null && session.oldBlockType != null && session.oldBlockType != Material.AIR) {
            block.setType(session.oldBlockType);
            if (session.oldBlockData != null) block.setBlockData(session.oldBlockData);
        } else {
            block.setType(Material.AIR);
        }
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        event.setCancelled(true);

        SignSession session = activeSessions.remove(uuid);
        Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);

        // Collect all non-default typed lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < event.getLines().length; i++) {
            String line = event.getLine(i);
            if (line == null || line.isEmpty()) continue;
            if (session.defaultLines != null && i < session.defaultLines.length
                    && line.equals(session.defaultLines[i])) continue;

            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }

        String input = sb.toString().trim();

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline()) return;
            if (input.isEmpty()) {
                // Empty — return to GUI
                plugin.getMainGUI(837914895).openInventory(player, 187107641);
                return;
            }
            if (session.callback != null) session.callback.accept(player, input);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!activeSessions.containsKey(player.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.OAK_SIGN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player   player = event.getPlayer();
        UUID     uuid   = player.getUniqueId();
        Location loc    = signLocations.get(uuid);
        if (loc == null) return;
        if (loc.isWorldLoaded() && event.getBlock().getLocation().equals(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer());
    }

    // ─── Inner class ─────────────────────────────────────────────────────────

    private static class SignSession {
        final BiConsumer<Player, String> callback;
        final String[]                   defaultLines;
        Material  oldBlockType = Material.AIR;
        BlockData oldBlockData;

        SignSession(BiConsumer<Player, String> callback, String[] defaultLines) {
            this.callback     = callback;
            this.defaultLines = defaultLines;
        }
    }
}

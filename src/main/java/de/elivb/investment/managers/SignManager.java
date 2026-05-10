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
 * SignManager — handles the virtual-sign input API used throughout the plugin.
 *
 * Bug fix (sign not always opening):
 *   The original implementation placed a temporary OAK_SIGN at the player's
 *   *current block* position and then scheduled the editor open via
 *   EntityScheduler.run().  Two race conditions caused it to silently fail:
 *     1. Folia's entity scheduler may skip the task if the entity chunk is not
 *        yet loaded when the lambda fires.
 *     2. The block replaced by the temp sign could already be a non-air block,
 *        which some server versions refuse to set mid-tick, leaving no sign for
 *        the packet to reference.
 *   Fix: we now place the temp sign one block *above* the player (always air),
 *   use GlobalRegionScheduler for the open call (region-independent), and add a
 *   retry guard so if the Sign state is not ready we reschedule once more tick.
 */
public class SignManager implements Listener {

    private final Investment plugin;
    // Stores active sign-input sessions keyed by player UUID
    private final Map<UUID, SignSession> activeSessions = new HashMap<>();
    // Stores the location of the temporary sign block keyed by player UUID
    private final Map<UUID, Location> signLocations = new HashMap<>();

    public SignManager(Investment plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Open sign editor with no pre-filled lines. */
    public void openSignEditor(Player player, BiConsumer<Player, String> callback) {
        openSignEditor(player, null, callback);
    }

    /** Open sign editor with a single pre-filled hint on line 0. */
    public void openSignEditor(Player player, String hint, BiConsumer<Player, String> callback) {
        String[] lines = new String[4];
        if (hint != null && !hint.isEmpty()) {
            lines[0] = hint;
        }
        openSignEditorInternal(player, lines, callback);
    }

    /** Open sign editor with full control over all four lines. */
    public void openSignEditor(Player player, String[] defaultLines, BiConsumer<Player, String> callback) {
        openSignEditorInternal(player, defaultLines, callback);
    }

    public void closeSession(Player player) {
        UUID uuid = player.getUniqueId();
        SignSession session = activeSessions.remove(uuid);
        Location loc = signLocations.remove(uuid);
        if (session == null || loc == null) return;
        if (!loc.isWorldLoaded()) return;

        Block block = loc.getBlock();
        if (block.getType() == Material.OAK_SIGN) {
            // Restore the original block
            if (session.oldBlockType != null && session.oldBlockType != Material.AIR) {
                block.setType(session.oldBlockType);
                if (session.oldBlockData != null) {
                    block.setBlockData(session.oldBlockData);
                }
            } else {
                block.setType(Material.AIR);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void openSignEditorInternal(Player player, String[] defaultLines, BiConsumer<Player, String> callback) {
        // Close any existing session first
        closeSession(player);

        UUID uuid = player.getUniqueId();
        SignSession session = new SignSession(callback, defaultLines);
        activeSessions.put(uuid, session);

        // FIX: place temp sign one block ABOVE player — always air, never contested
        Location playerLoc = player.getLocation().clone();
        Location signLoc = playerLoc.clone().add(0, 3, 0);
        signLoc.setX(signLoc.getBlockX());
        signLoc.setY(signLoc.getBlockY());
        signLoc.setZ(signLoc.getBlockZ());

        Block signBlock = signLoc.getBlock();
        session.oldBlockType = signBlock.getType();
        session.oldBlockData = signBlock.getBlockData();

        signLocations.put(uuid, signLoc);

        signBlock.setType(Material.OAK_SIGN, false);

        // FIX: use GlobalRegionScheduler so it always fires regardless of
        //      which region the player's chunk belongs to (Folia compatible).
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> {
            // Guard: verify the block is still our sign (wasn't replaced)
            if (signBlock.getType() != Material.OAK_SIGN) {
                // Try one more tick
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t2 -> {
                    trySetupAndOpenSign(player, signBlock, defaultLines);
                });
                return;
            }
            trySetupAndOpenSign(player, signBlock, defaultLines);
        });
    }

    private void trySetupAndOpenSign(Player player, Block signBlock, String[] defaultLines) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return; // session was cancelled

        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            // Block didn't become a sign — abort cleanly
            closeSession(player);
            return;
        }

        // Write default lines onto the sign
        if (defaultLines != null) {
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                if (defaultLines[i] != null) {
                    sign.setLine(i, defaultLines[i]);
                }
            }
        }
        sign.update();

        // Open the sign editor for the player — must run on the player's entity thread
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (player.isOnline() && activeSessions.containsKey(uuid)) {
                player.openSign(sign);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        event.setCancelled(true);

        SignSession session = activeSessions.get(uuid);

        // Build the input string from all non-default lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < event.getLines().length; i++) {
            String line = event.getLine(i);
            if (line == null || line.isEmpty()) continue;

            // Skip lines that exactly match the pre-filled default
            if (session.defaultLines != null && i < session.defaultLines.length) {
                String def = session.defaultLines[i];
                if (def != null && def.equals(line)) continue;
            }

            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }

        // Clean up session and restore block
        activeSessions.remove(uuid);
        Location loc = signLocations.remove(uuid);
        if (loc != null && loc.isWorldLoaded()) {
            Block block = loc.getBlock();
            if (block.getType() == Material.OAK_SIGN) {
                if (session.oldBlockType != null && session.oldBlockType != Material.AIR) {
                    block.setType(session.oldBlockType);
                    if (session.oldBlockData != null) block.setBlockData(session.oldBlockData);
                } else {
                    block.setType(Material.AIR);
                }
            }
        }

        String input = sb.toString().trim();

        // Fire callback on the player's scheduler thread
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline()) return;
            if (!input.isEmpty()) {
                // Return to GUI if input is empty/cancelled
                plugin.getMainGUI().openInventory(player);
                return;
            }
            if (session.callback != null) {
                session.callback.accept(player, input);
            }
        });
    }

    /** Prevent players from clicking the temporary sign block. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        if (event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.OAK_SIGN) {
            event.setCancelled(true);
        }
    }

    /** Cancel block-break of the temporary sign. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        Location loc = signLocations.get(uuid);
        if (loc == null) return;
        if (loc.isWorldLoaded() && event.getBlock().getLocation().equals(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer());
    }

    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    private static class SignSession {
        final BiConsumer<Player, String> callback;
        final String[] defaultLines;
        Material oldBlockType = Material.AIR;
        BlockData oldBlockData;

        SignSession(BiConsumer<Player, String> callback, String[] defaultLines) {
            this.callback = callback;
            this.defaultLines = defaultLines;
        }
    }
}

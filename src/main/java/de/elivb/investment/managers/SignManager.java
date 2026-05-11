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
 * SignManager v1.3 — patched sign editor.
 *
 * All constructor and method signatures exactly match the original compiled class
 * so Investment.onEnable() can call new SignManager(this, 827996154) without error.
 *
 * BUG FIX: Original placed temp sign at player feet (often non-air) then used
 * EntityScheduler which Folia can silently skip. Fix: sign goes 3 blocks above
 * the player (always air) + GlobalRegionScheduler (never skipped) + retry guard.
 */
public class SignManager implements Listener {

    private final Investment plugin;
    private final Map<UUID, SignSession> activeSessions = new HashMap<>();
    private final Map<UUID, Location>   signLocations  = new HashMap<>();

    // ── Constructor: must be (Investment, int) ────────────────────────────────
    public SignManager(final Investment plugin, final int n) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Public API — signatures match original exactly ────────────────────────

    /**
     * openSignEditor(Player p0, Player p2, String[] p3, BiConsumer p4)
     * The second Player param is an obfuscation artefact; p0 is the real player.
     */
    public void openSignEditor(final Player player, final Player ignored,
                               final String[] defaultLines,
                               final BiConsumer<Player, String> callback) {
        openInternal(player, defaultLines, callback);
    }

    /** openSignEditor(Player, String, BiConsumer) */
    public void openSignEditor(final Player player, final String hint,
                               final BiConsumer<Player, String> callback) {
        String[] lines = new String[4];
        if (hint != null && !hint.isEmpty()) lines[0] = hint;
        openInternal(player, lines, callback);
    }

    /** openSignEditor(Player, BiConsumer) */
    public void openSignEditor(final Player player,
                               final BiConsumer<Player, String> callback) {
        openInternal(player, null, callback);
    }

    /** closeSession(Player, int) */
    public void closeSession(final Player player, final int n) {
        UUID        uuid    = player.getUniqueId();
        SignSession session = activeSessions.remove(uuid);
        Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void openInternal(Player player, String[] defaultLines,
                               BiConsumer<Player, String> callback) {
        closeSession(player, 0);

        UUID        uuid    = player.getUniqueId();
        SignSession session = new SignSession(callback, defaultLines, 0);
        activeSessions.put(uuid, session);

        // FIX: always air 3 blocks above — no block placement conflicts
        Location signLoc = player.getLocation().clone().add(0, 3, 0);
        signLoc.setX(signLoc.getBlockX());
        signLoc.setY(signLoc.getBlockY());
        signLoc.setZ(signLoc.getBlockZ());

        Block signBlock = signLoc.getBlock();
        session.oldBlockType = signBlock.getType();
        session.oldBlockData  = signBlock.getBlockData();
        signLocations.put(uuid, signLoc);

        signBlock.setType(Material.OAK_SIGN, false);

        // FIX: GlobalRegionScheduler — fires on Folia regardless of region
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (!player.isOnline() || !activeSessions.containsKey(uuid)) return;
            if (signBlock.getType() != Material.OAK_SIGN) {
                // retry one more tick
                plugin.getServer().getGlobalRegionScheduler().run(plugin, t2 -> {
                    if (player.isOnline() && activeSessions.containsKey(uuid))
                        tryOpen(player, uuid, signBlock, defaultLines);
                });
                return;
            }
            tryOpen(player, uuid, signBlock, defaultLines);
        });
    }

    private void tryOpen(Player player, UUID uuid, Block signBlock, String[] defaultLines) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign sign)) {
            closeSession(player, 0);
            return;
        }
        if (defaultLines != null) {
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                if (defaultLines[i] != null) sign.setLine(i, defaultLines[i]);
            }
        }
        sign.update();
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            if (player.isOnline() && activeSessions.containsKey(uuid))
                player.openSign(sign);
        });
    }

    private void restoreBlock(Location loc, SignSession session) {
        if (loc == null || !loc.isWorldLoaded()) return;
        Block block = loc.getBlock();
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
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        event.setCancelled(true);

        SignSession session = activeSessions.remove(uuid);
        Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);

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
                plugin.getMainGUI(837914895).openInventory(player, 187107641);
                return;
            }
            if (session.callback != null) session.callback.accept(player, input);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (!activeSessions.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.OAK_SIGN)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        Location loc = signLocations.get(event.getPlayer().getUniqueId());
        if (loc != null && loc.isWorldLoaded()
                && event.getBlock().getLocation().equals(loc))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        closeSession(event.getPlayer(), 759738250);
    }

    // ── Inner class: must expose (BiConsumer, String[], int) constructor ───────

    static class SignSession {
        final BiConsumer<Player, String> callback;
        final String[]                   defaultLines;
        Material  oldBlockType = Material.AIR;
        BlockData oldBlockData;

        SignSession(BiConsumer<Player, String> callback, String[] defaultLines, int n) {
            this.callback     = callback;
            this.defaultLines = defaultLines;
        }
    }
}

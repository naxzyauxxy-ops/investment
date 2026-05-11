package de.elivb.investment.managers;

import de.elivb.investment.Investment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
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
 * SignManager v1.3
 *
 * Fixes:
 * 1. player.openSign(sign, Side.FRONT) — required in Paper 1.21+
 * 2. player.getScheduler().run() called DIRECTLY — no GlobalRegionScheduler wrapper
 * 3. Sign placed 3 blocks above player — always air, never a solid block
 */
public class SignManager implements Listener {

    private final Investment plugin;
    private final Map<UUID, SignSession> activeSessions = new HashMap<>();
    private final Map<UUID, Location>   signLocations  = new HashMap<>();

    public SignManager(final Investment plugin, final int n) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

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

    private void openInternal(final Player player, final String[] defaultLines,
                              final BiConsumer<Player, String> callback) {
        closeSession(player, 0);

        final UUID        uuid    = player.getUniqueId();
        // Match original SignSession constructor: (BiConsumer, BiConsumer, String[])
        // The obfuscator swapped param types — we just use our clean version
        final SignSession session = new SignSession(callback, defaultLines);
        activeSessions.put(uuid, session);

        // 3 blocks above = always air
        final Location signLoc = player.getLocation().clone();
        signLoc.setX(signLoc.getBlockX());
        signLoc.setY(signLoc.getBlockY() + 3);
        signLoc.setZ(signLoc.getBlockZ());

        final Block signBlock = signLoc.getBlock();
        session.oldBlockType = signBlock.getType();
        session.oldBlockData  = signBlock.getBlockData();
        signLocations.put(uuid, signLoc);

        signBlock.setType(Material.OAK_SIGN);

        // Write default lines onto the sign NOW before scheduling
        final BlockState state = signBlock.getState();
        if (state instanceof Sign sign) {
            if (defaultLines != null) {
                for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                    if (defaultLines[i] != null) sign.setLine(i, defaultLines[i]);
                }
            }
            sign.update(true);
        }

        // Call EntityScheduler directly — matches original bytecode exactly
        // Do NOT wrap in GlobalRegionScheduler (causes double-schedule / stale state)
        player.getScheduler().run(plugin, t -> {
            if (!player.isOnline() || !activeSessions.containsKey(uuid)) return;
            final BlockState fresh = signBlock.getState();
            if (fresh instanceof Sign freshSign) {
                // openSign(Sign, Side.FRONT) required in Paper 1.21+
                player.openSign(freshSign, Side.FRONT);
            }
        }, null);
    }

    private void restoreBlock(final Location loc, final SignSession session) {
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(final SignChangeEvent event) {
        final Player player = event.getPlayer();
        final UUID   uuid   = player.getUniqueId();
        if (!activeSessions.containsKey(uuid)) return;

        event.setCancelled(true);

        final SignSession session = activeSessions.remove(uuid);
        final Location    loc     = signLocations.remove(uuid);
        restoreBlock(loc, session);

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
                && event.getClickedBlock().getType() == Material.OAK_SIGN)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Location loc = signLocations.get(event.getPlayer().getUniqueId());
        if (loc != null && loc.isWorldLoaded()
                && event.getBlock().getLocation().equals(loc))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        closeSession(event.getPlayer(), 759738250);
    }

    // Must have (BiConsumer, BiConsumer, String[]) constructor to match original bytecode
    @SuppressWarnings("rawtypes")
    static class SignSession {
        final BiConsumer<Player, String> callback;
        final String[]                   defaultLines;
        Material  oldBlockType = Material.AIR;
        BlockData oldBlockData;

        SignSession(final BiConsumer<Player, String> callback, final String[] defaultLines) {
            this.callback     = callback;
            this.defaultLines = defaultLines;
        }

        // Original obfuscated constructor signature: (BiConsumer, BiConsumer<Player,String>, String[])
        SignSession(final BiConsumer callback,
                    final BiConsumer<Player, String> ignoredDefaultLines,
                    final String[] array) {
            //noinspection unchecked
            this.callback     = (BiConsumer<Player, String>) callback;
            this.defaultLines = array;
        }
    }
}

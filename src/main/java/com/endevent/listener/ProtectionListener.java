package com.endevent.listener;

import com.endevent.VoidEventPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Protege el evento: evita romper bloques, daño durante cinemáticas,
 * y maneja la muerte en el vacío.
 */
public class ProtectionListener implements Listener {

    private final VoidEventPlugin plugin;

    public ProtectionListener(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (plugin.getEventManager().isRunning() && !e.getPlayer().isOp()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (plugin.getEventManager().isRunning() && !e.getPlayer().isOp()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (plugin.getCinematicEngine().isActive() && plugin.getCinematicEngine().getViewers().contains(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!plugin.getEventManager().isRunning()) return;
        Player p = e.getEntity();
        // Respawn rápido en la arena
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isDead()) {
                p.spigot().respawn();
            }
            if (plugin.getArenaManager().getArenaCenter() != null) {
                p.teleport(plugin.getArenaManager().getArenaCenter().clone().add(0, 2, 0));
                p.setGameMode(GameMode.SURVIVAL);
            }
        }, 20L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getEventManager().isRunning()) return;
        Player p = e.getPlayer();

        // Durante cinemáticas: bloquear movimiento Y rotación de cámara
        if (plugin.getCinematicEngine().isActive() && plugin.getCinematicEngine().getViewers().contains(p)) {
            e.setCancelled(true);
            return;
        }

        // Si cae al vacío durante la pelea, teleportar de vuelta
        if (p.getLocation().getY() < 0 && p.getGameMode() == GameMode.SURVIVAL) {
            if (plugin.getArenaManager().getArenaCenter() != null) {
                p.teleport(plugin.getArenaManager().getArenaCenter().clone().add(0, 2, 0));
                p.damage(5.0); // Daño por caída al vacío
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Si el jugador se desconecta durante el evento, limpieza
    }
}

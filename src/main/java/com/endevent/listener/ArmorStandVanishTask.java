package com.endevent.listener;

import com.endevent.VoidEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Oculta completamente los ArmorStands (invisibles/marker) para los jugadores
 * en modo espectador, usando hideEntity para que ni siquiera se vean translúcidos.
 */
public class ArmorStandVanishTask {

    private final VoidEventPlugin plugin;
    private BukkitTask task;

    public ArmorStandVanishTask(VoidEventPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) {
                        for (ArmorStand as : p.getWorld().getEntitiesByClass(ArmorStand.class)) {
                            if (!as.isVisible() || as.isMarker()) {
                                try { p.hideEntity(plugin, as); } catch (Exception ignored) {}
                            }
                        }
                    } else {
                        for (ArmorStand as : p.getWorld().getEntitiesByClass(ArmorStand.class)) {
                            try { p.showEntity(plugin, as); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public void stop() {
        if (task != null && !task.isCancelled()) task.cancel();
    }
}

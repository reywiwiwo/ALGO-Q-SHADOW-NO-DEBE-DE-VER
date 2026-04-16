package com.endevent.listener;

import com.endevent.VoidEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Maneja la habilidad "Impulso Ascendente" de las Alas de la Luz.
 * Doble salto en el suelo → impulso vertical + inicio de planeo.
 */
public class ElytraAbilityListener implements Listener {

    private final VoidEventPlugin plugin;
    private final NamespacedKey elytraKey;
    private final Set<UUID> cooldown = new HashSet<>();

    public ElytraAbilityListener(VoidEventPlugin plugin) {
        this.plugin = plugin;
        this.elytraKey = new NamespacedKey(plugin, "alas_de_la_luz");
    }

    private boolean hasSpecialElytra(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) return false;
        ItemMeta meta = chestplate.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(elytraKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!hasSpecialElytra(p)) return;

        // Cuando el jugador está en el suelo, permitir "vuelo" para detectar doble salto
        if (p.isOnGround() && !p.isFlying()) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!hasSpecialElytra(p)) return;
        if (!p.isOnGround()) return;
        if (cooldown.contains(p.getUniqueId())) return;

        // Cancelar el vuelo vanilla
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);

        // Cooldown de 3 segundos
        cooldown.add(p.getUniqueId());
        new BukkitRunnable() {
            public void run() {
                cooldown.remove(p.getUniqueId());
            }
        }.runTaskLater(plugin, 60L);

        // Impulso hacia arriba
        p.setVelocity(new Vector(0, 1.5, 0));

        // Efectos visuales y sonido
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 0.5, 0), 40, 0.5, 0.3, 0.5, 0.08);

        // Mensaje en la ActionBar
        p.sendActionBar(Component.text("✦ Impulso Ascendente ✦", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        // Activar planeo tras un breve momento (cuando ya está en el aire)
        new BukkitRunnable() {
            public void run() {
                if (p.isOnline() && !p.isOnGround()) {
                    p.setGliding(true);
                }
            }
        }.runTaskLater(plugin, 8L);
    }
}

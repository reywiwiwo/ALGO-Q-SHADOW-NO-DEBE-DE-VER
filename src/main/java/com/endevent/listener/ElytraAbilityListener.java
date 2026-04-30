package com.endevent.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.endevent.VoidEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
 * Shift + Espacio en el suelo → impulso tipo carga de viento + inicio de planeo.
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

    /**
     * Detecta salto (Shift + Espacio mientras agachado en el suelo).
     * PlayerJumpEvent de Paper fira fiablemente incluso agachado,
     * a diferencia de PlayerToggleFlightEvent que Minecraft bloquea con Shift.
     */
    @EventHandler
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!hasSpecialElytra(p)) return;
        if (!p.isSneaking()) return; // Solo Shift + Espacio
        if (cooldown.contains(p.getUniqueId())) return;

        // Cooldown de 4 segundos
        cooldown.add(p.getUniqueId());
        new BukkitRunnable() {
            public void run() { cooldown.remove(p.getUniqueId()); }
        }.runTaskLater(plugin, 80L);

        final Location origin = p.getLocation().clone();
        final World world = p.getWorld();

        // ── Impulso potente hacia arriba ──
        p.setVelocity(new Vector(0, 1.8, 0));

        // ── Sonidos: explosión de viento ──
        world.playSound(origin, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.8f);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        world.playSound(origin, Sound.ITEM_TRIDENT_THROW, 0.8f, 0.5f);

        // ── Explosión inicial de partículas (nube + onda) ──
        world.spawnParticle(Particle.CLOUD, origin.clone().add(0, 0.5, 0), 50, 0.6, 0.2, 0.6, 0.15);
        world.spawnParticle(Particle.EXPLOSION, origin.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0);
        world.spawnParticle(Particle.END_ROD, origin.clone().add(0, 0.5, 0), 30, 0.4, 0.2, 0.4, 0.1);

        // ── Anillo expansivo en el suelo (tipo onda de choque) ──
        new BukkitRunnable() {
            double radius = 0.5;
            @Override
            public void run() {
                if (radius > 5.0) { cancel(); return; }
                int points = (int)(radius * 12);
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = origin.getX() + radius * Math.cos(angle);
                    double z = origin.getZ() + radius * Math.sin(angle);
                    Location point = new Location(world, x, origin.getY() + 0.1, z);
                    Particle.DustOptions dust = new Particle.DustOptions(
                            Color.fromRGB(200, 230, 255), 1.5f);
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
                    if (i % 3 == 0) {
                        world.spawnParticle(Particle.CLOUD, point, 1, 0.1, 0.05, 0.1, 0.01);
                    }
                }
                radius += 0.8;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // ── Estela de partículas mientras sube ──
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!p.isOnline() || ticks > 15) { cancel(); return; }
                Location loc = p.getLocation();
                world.spawnParticle(Particle.CLOUD, loc, 8, 0.3, 0.1, 0.3, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, 5, 0.2, 0.3, 0.2, 0.02);
                Particle.DustOptions trail = new Particle.DustOptions(
                        Color.fromRGB(255, 215, 0), 1.2f);
                world.spawnParticle(Particle.DUST, loc, 4, 0.2, 0.1, 0.2, 0, trail);
                ticks++;
            }
        }.runTaskTimer(plugin, 2L, 1L);

        // ActionBar
        p.sendActionBar(Component.text("✦ Impulso Ascendente ✦", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        // Activar planeo cuando alcanza altura
        new BukkitRunnable() {
            public void run() {
                if (p.isOnline() && !p.isOnGround()) {
                    p.setGliding(true);
                    world.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.5f);
                }
            }
        }.runTaskLater(plugin, 10L);
    }
}

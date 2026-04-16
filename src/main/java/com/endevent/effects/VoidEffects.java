package com.endevent.effects;

import com.endevent.VoidEventPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class VoidEffects {

    private final VoidEventPlugin plugin;
    private final List<BukkitTask> activeTasks = new ArrayList<>();

    public VoidEffects(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Dibuja un circulo de particulas de colores (morado, azul, cyan, magenta)
     */
    public BukkitTask spawnColorCircle(Location center, double radius, int points) {
        BukkitTask task = new BukkitRunnable() {
            double rotation = 0;
            @Override
            public void run() {
                World world = center.getWorld();
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i + rotation;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location point = new Location(world, x, center.getY(), z);

                    Color color = getCircleColor(i, points);
                    Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
                    world.spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0, dust);

                    if (i % 3 == 0) {
                        world.spawnParticle(Particle.PORTAL, point, 1, 0.1, 0.3, 0.1, 0.05);
                    }
                }
                rotation += 0.02;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        activeTasks.add(task);
        return task;
    }

    private Color getCircleColor(int index, int total) {
        double ratio = (double) index / total;
        if (ratio < 0.25) {
            return Color.fromRGB(128, 0, 255); // Morado
        } else if (ratio < 0.5) {
            return Color.fromRGB(0, 100, 255); // Azul
        } else if (ratio < 0.75) {
            return Color.fromRGB(0, 200, 255); // Cyan
        } else {
            return Color.fromRGB(200, 0, 255); // Magenta
        }
    }

    /**
     * Dibuja un rayo/linea de particulas entre dos puntos
     */
    public void drawBeam(Location from, Location to, Particle particle, int density) {
        drawBeam(from, to, particle, density, null);
    }

    public <T> void drawBeam(Location from, Location to, Particle particle, int density, T data) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (int i = 0; i < density; i++) {
            double t = (double) i / density;
            Location point = from.clone().add(direction.clone().multiply(distance * t));
            if (data != null) {
                world.spawnParticle(particle, point, 1, 0.02, 0.02, 0.02, 0, data);
            } else {
                world.spawnParticle(particle, point, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    /**
     * Rayo negro (oscuro) desde un punto a otro — simula el rayo reflejado al huevo
     */
    public void drawDarkBeam(Location from, Location to, int density) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (int i = 0; i < density; i++) {
            double t = (double) i / density;
            Location point = from.clone().add(direction.clone().multiply(distance * t));

            Particle.DustOptions blackDust = new Particle.DustOptions(Color.fromRGB(10, 0, 20), 1.2f);
            world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0, blackDust);
            if (i % 3 == 0) {
                Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(80, 0, 120), 0.8f);
                world.spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0, purpleDust);
            }
        }
    }

    /**
     * Rayo estilo cristal del End (rosado/blanco)
     */
    public void drawCrystalBeam(Location from, Location to, int density) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (int i = 0; i < density; i++) {
            double t = (double) i / density;
            Location point = from.clone().add(direction.clone().multiply(distance * t));

            world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0);
            if (i % 2 == 0) {
                Particle.DustOptions pinkDust = new Particle.DustOptions(Color.fromRGB(255, 150, 255), 1.0f);
                world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0, pinkDust);
            }
        }
    }

    /**
     * Partículas de muerte del dragón (como cuando muere el Ender Dragon vanilla)
     */
    public BukkitTask spawnDragonDeathParticles(Location center, int durationTicks) {
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks) {
                    cancel();
                    return;
                }
                World world = center.getWorld();
                for (int i = 0; i < 15; i++) {
                    double rx = (Math.random() - 0.5) * 4;
                    double ry = Math.random() * 6;
                    double rz = (Math.random() - 0.5) * 4;
                    Location p = center.clone().add(rx, ry, rz);
                    world.spawnParticle(Particle.DRAGON_BREATH, p, 1, 0, 0, 0, 0.02, 1.0f);
                    world.spawnParticle(Particle.END_ROD, p, 1, 0, 0.1, 0, 0.01);
                }
                double radius = 1.5 + ticks * 0.03;
                for (int i = 0; i < 20; i++) {
                    double angle = (2 * Math.PI / 20) * i;
                    Location ring = center.clone().add(radius * Math.cos(angle), 0.5, radius * Math.sin(angle));
                    world.spawnParticle(Particle.PORTAL, ring, 2, 0.1, 0.1, 0.1, 0.5);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        activeTasks.add(task);
        return task;
    }

    /**
     * Explosion visual grande
     */
    public void bigExplosion(Location loc) {
        World world = loc.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 1, 1, 1, 0);
        world.spawnParticle(Particle.FLAME, loc, 50, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.LAVA, loc, 20, 1, 1, 1, 0);
    }

    /**
     * Aura alrededor de un mob gigante (partículas oscuras)
     */
    public BukkitTask spawnGiantAura(org.bukkit.entity.Entity entity) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                Location loc = entity.getLocation().add(0, 6, 0);
                World world = entity.getWorld();
                for (int i = 0; i < 10; i++) {
                    double rx = (Math.random() - 0.5) * 6;
                    double ry = (Math.random() - 0.5) * 12;
                    double rz = (Math.random() - 0.5) * 6;
                    Location p = loc.clone().add(rx, ry, rz);
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(30, 0, 50), 2.0f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
        activeTasks.add(task);
        return task;
    }

    /**
     * Partículas de grietas en el suelo (para la destrucción de la isla)
     */
    public void spawnCrackParticles(Location center, double radius) {
        World world = center.getWorld();
        for (int i = 0; i < 100; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double r = Math.random() * radius;
            Location p = center.clone().add(r * Math.cos(angle), 0.5, r * Math.sin(angle));
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.5f);
            world.spawnParticle(Particle.DUST, p, 3, 0.2, 0.1, 0.2, 0, dust);
            world.spawnParticle(Particle.SMOKE, p, 2, 0.1, 0.2, 0.1, 0.02);
        }
    }

    /**
     * Partículas de renacimiento del dragón de luz
     */
    public BukkitTask spawnLightBirthEffect(Location center, int durationTicks) {
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks) {
                    cancel();
                    return;
                }
                World world = center.getWorld();
                double progress = (double) ticks / durationTicks;
                double radius = 1 + progress * 5;
                int particles = (int)(20 + progress * 60);

                for (int i = 0; i < particles; i++) {
                    double angle = (2 * Math.PI / particles) * i;
                    double y = progress * 4;
                    Location p = center.clone().add(radius * Math.cos(angle), y * Math.sin(angle * 3), radius * Math.sin(angle));
                    world.spawnParticle(Particle.END_ROD, p, 1, 0, 0.1, 0, 0.02);

                    Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
                    world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, gold);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        activeTasks.add(task);
        return task;
    }

    public void cancelAll() {
        activeTasks.forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        activeTasks.clear();
    }
}

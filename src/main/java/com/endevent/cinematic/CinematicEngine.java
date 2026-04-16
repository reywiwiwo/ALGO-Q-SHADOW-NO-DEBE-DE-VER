package com.endevent.cinematic;

import com.endevent.VoidEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

/**
 * Motor de cinemáticas — maneja cámara en modo espectador,
 * interpolación de keyframes y textos en pantalla.
 */
public class CinematicEngine {

    private final VoidEventPlugin plugin;
    private final List<Player> viewers = new ArrayList<>();
    private final Map<Player, GameMode> previousGameModes = new HashMap<>();
    private final Map<Player, Location> previousLocations = new HashMap<>();
    private BukkitTask currentCameraTask;
    private boolean active = false;

    public CinematicEngine(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Pone a los jugadores en modo cinemática (espectador, inmóvil)
     */
    public void enterCinematicMode(Collection<? extends Player> players) {
        active = true;
        viewers.clear();
        for (Player p : players) {
            previousGameModes.put(p, p.getGameMode());
            previousLocations.put(p, p.getLocation().clone());
            p.setGameMode(GameMode.SPECTATOR);
            viewers.add(p);
        }
    }

    /**
     * Restaura a los jugadores a su modo anterior
     */
    public void exitCinematicMode() {
        active = false;
        if (currentCameraTask != null && !currentCameraTask.isCancelled()) {
            currentCameraTask.cancel();
        }
        for (Player p : viewers) {
            if (p.isOnline()) {
                GameMode prev = previousGameModes.getOrDefault(p, GameMode.SURVIVAL);
                p.setGameMode(prev);
            }
        }
        previousGameModes.clear();
        previousLocations.clear();
    }

    /**
     * Teleporta a los jugadores a donde estaban antes
     */
    public void restoreLocations() {
        for (Player p : viewers) {
            if (p.isOnline() && previousLocations.containsKey(p)) {
                p.teleport(previousLocations.get(p));
            }
        }
    }

    /**
     * Mueve la cámara de forma suave entre keyframes
     * @param keyframes lista de posiciones con duración en ticks entre cada una
     * @param onComplete se ejecuta al terminar
     */
    public void playCameraPath(List<CameraKeyframe> keyframes, Runnable onComplete) {
        if (keyframes.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        currentCameraTask = new BukkitRunnable() {
            int keyframeIndex = 0;
            int tickInSegment = 0;

            @Override
            public void run() {
                if (!active || keyframeIndex >= keyframes.size() - 1) {
                    cancel();
                    if (onComplete != null) {
                        new BukkitRunnable() { public void run() { onComplete.run(); } }
                                .runTask(plugin);
                    }
                    return;
                }

                CameraKeyframe from = keyframes.get(keyframeIndex);
                CameraKeyframe to = keyframes.get(keyframeIndex + 1);
                int duration = from.getDurationTicks();

                double t = (double) tickInSegment / duration;
                t = smoothStep(t);

                Location interpolated = interpolate(from.getLocation(), to.getLocation(), t);

                for (Player p : viewers) {
                    if (p.isOnline()) {
                        p.teleport(interpolated);
                    }
                }

                tickInSegment++;
                if (tickInSegment >= duration) {
                    tickInSegment = 0;
                    keyframeIndex++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Toma estática: teleporta a todos a una posición y espera cierta duración
     */
    public void staticShot(Location location, int durationTicks, Runnable onComplete) {
        for (Player p : viewers) {
            if (p.isOnline()) p.teleport(location);
        }
        new BukkitRunnable() {
            public void run() { if (onComplete != null) onComplete.run(); }
        }.runTaskLater(plugin, durationTicks);
    }

    /**
     * Toma de paneo suave entre dos puntos
     */
    public void panShot(Location from, Location to, int durationTicks, Runnable onComplete) {
        List<CameraKeyframe> path = new ArrayList<>();
        path.add(new CameraKeyframe(from, durationTicks));
        path.add(new CameraKeyframe(to, 0));
        playCameraPath(path, onComplete);
    }

    /**
     * Muestra un diálogo con título + subtítulo (estilo cinemático)
     */
    public void showDialog(String speaker, String text, NamedTextColor color, int durationTicks) {
        Component title = Component.text(speaker, color, TextDecoration.BOLD);
        Component subtitle = Component.text(text, NamedTextColor.WHITE);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(durationTicks * 50L),
                Duration.ofMillis(500)
        );
        Title titleObj = Title.title(title, subtitle, times);
        for (Player p : viewers) {
            if (p.isOnline()) p.showTitle(titleObj);
        }
    }

    /**
     * Muestra solo subtítulo (para diálogo largo)
     */
    public void showSubtitle(String text, NamedTextColor color, int durationTicks) {
        Component title = Component.empty();
        Component subtitle = Component.text(text, color);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofMillis(durationTicks * 50L),
                Duration.ofMillis(300)
        );
        for (Player p : viewers) {
            if (p.isOnline()) p.showTitle(Title.title(title, subtitle, times));
        }
    }

    /**
     * Muestra un actionbar
     */
    public void showActionBar(String text, NamedTextColor color) {
        Component msg = Component.text(text, color);
        for (Player p : viewers) {
            if (p.isOnline()) p.sendActionBar(msg);
        }
    }

    /**
     * Teleporta a todos los espectadores a una ubicación
     */
    public void teleportAll(Location loc) {
        for (Player p : viewers) {
            if (p.isOnline()) p.teleport(loc);
        }
    }

    /**
     * Transición suave entre la posición actual de la cámara y un destino.
     * Útil para conectar fases sin cortes bruscos.
     * @param to destino final
     * @param durationTicks duración de la transición
     * @param onComplete callback al terminar
     */
    public void smoothTransition(Location to, int durationTicks, Runnable onComplete) {
        if (viewers.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        // Capturar posición actual como origen
        final Location from = viewers.get(0).getLocation().clone();
        panShot(from, to, durationTicks, onComplete);
    }

    /**
     * Transición suave sin callback — solo mueve la cámara.
     */
    public void smoothTransition(Location to, int durationTicks) {
        smoothTransition(to, durationTicks, null);
    }

    public List<Player> getViewers() { return viewers; }
    public boolean isActive() { return active; }

    // --- Utilidades de interpolación ---

    private double smoothStep(double t) {
        return t * t * (3 - 2 * t);
    }

    private Location interpolate(Location a, Location b, double t) {
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double y = a.getY() + (b.getY() - a.getY()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        float yaw = lerpAngle(a.getYaw(), b.getYaw(), (float) t);
        float pitch = (float) (a.getPitch() + (b.getPitch() - a.getPitch()) * t);
        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    private float lerpAngle(float a, float b, float t) {
        float diff = ((b - a + 540) % 360) - 180;
        return a + diff * t;
    }

    /**
     * Record para un keyframe de cámara
     */
    public static class CameraKeyframe {
        private final Location location;
        private final int durationTicks;

        public CameraKeyframe(Location location, int durationTicks) {
            this.location = location;
            this.durationTicks = durationTicks;
        }

        public Location getLocation() { return location; }
        public int getDurationTicks() { return durationTicks; }
    }
}

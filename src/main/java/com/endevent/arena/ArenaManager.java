package com.endevent.arena;

import com.endevent.VoidEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Gestiona las mini-islas flotantes durante la pelea
 * y la destrucción/regeneración de la isla del End.
 */
public class ArenaManager {

    private final VoidEventPlugin plugin;
    private final List<FloatingIsland> activeIslands = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask islandSpawnerTask;
    private BukkitTask islandManagerTask;
    private Location arenaCenter;
    private boolean active = false;

    private static final int MIN_ISLANDS = 2;
    private static final int MAX_ISLANDS = 5;
    private static final double ISLAND_SPAWN_RADIUS = 35.0;
    private static final int ISLAND_LIFETIME_MIN = 200; // 10s
    private static final int ISLAND_LIFETIME_MAX = 400; // 20s
    private static final int ISLAND_SIZE_MIN = 3;
    private static final int ISLAND_SIZE_MAX = 6;
    private static final double ISLAND_Y_MIN = 55;
    private static final double ISLAND_Y_MAX = 70;

    public ArenaManager(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia el sistema de islas flotantes
     */
    public void startIslandSystem(Location center) {
        this.arenaCenter = center;
        this.active = true;
        activeIslands.clear();

        // Spawn inicial de islas
        for (int i = 0; i < MIN_ISLANDS + 1; i++) {
            spawnRandomIsland();
        }

        // Tarea para mantener mínimo de islas y spawnear nuevas
        islandSpawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                if (activeIslands.size() < MIN_ISLANDS) {
                    spawnRandomIsland();
                }
                if (activeIslands.size() < MAX_ISLANDS && random.nextInt(100) < 30) {
                    spawnRandomIsland();
                }
            }
        }.runTaskTimer(plugin, 40L, 60L);

        // Tarea para gestionar el ciclo de vida de las islas
        islandManagerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) { cancel(); return; }
                Iterator<FloatingIsland> it = activeIslands.iterator();
                while (it.hasNext()) {
                    FloatingIsland island = it.next();
                    island.tick();
                    if (island.isExpired()) {
                        // Antes de destruir, asegurar que habrá suficientes islas
                        if (activeIslands.size() > MIN_ISLANDS) {
                            island.collapse();
                            it.remove();
                        } else {
                            // Extender vida si no hay suficientes
                            island.extendLife(100);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Detiene el sistema de islas
     */
    public void stopIslandSystem() {
        active = false;
        if (islandSpawnerTask != null) islandSpawnerTask.cancel();
        if (islandManagerTask != null) islandManagerTask.cancel();
        for (FloatingIsland island : activeIslands) {
            island.collapse();
        }
        activeIslands.clear();
    }

    private void spawnRandomIsland() {
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 10 + random.nextDouble() * ISLAND_SPAWN_RADIUS;
        double x = arenaCenter.getX() + radius * Math.cos(angle);
        double z = arenaCenter.getZ() + radius * Math.sin(angle);
        double y = ISLAND_Y_MIN + random.nextDouble() * (ISLAND_Y_MAX - ISLAND_Y_MIN);
        int size = ISLAND_SIZE_MIN + random.nextInt(ISLAND_SIZE_MAX - ISLAND_SIZE_MIN + 1);
        int lifetime = ISLAND_LIFETIME_MIN + random.nextInt(ISLAND_LIFETIME_MAX - ISLAND_LIFETIME_MIN);

        Location loc = new Location(arenaCenter.getWorld(), x, y, z);
        FloatingIsland island = new FloatingIsland(loc, size, lifetime);
        island.build();
        activeIslands.add(island);
    }

    /**
     * Destruye la isla del End de forma dramática (grietas + falling blocks)
     */
    public void destroyEndIsland(Location center, double radius, Runnable onComplete) {
        World world = center.getWorld();
        int cx = (int) center.getX();
        int cz = (int) center.getZ();

        // Calcular posiciones de bloques asíncronamente, aplicar en batches por tick
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Pre-calcular todos los anillos
            List<List<int[]>> rings = new ArrayList<>();
            for (double r = 0; r < radius; r += 5.0) {
                List<int[]> ringBlocks = new ArrayList<>();
                for (int x = (int) -r; x <= r; x++) {
                    for (int z = (int) -r; z <= r; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        if (dist >= r - 5.0 && dist < r && dist >= 4) {
                            ringBlocks.add(new int[]{cx + x, cz + z});
                        }
                    }
                }
                if (!ringBlocks.isEmpty()) rings.add(ringBlocks);
            }

            // Aplicar cada anillo en el hilo principal, 1 anillo cada 8 ticks
            Bukkit.getScheduler().runTask(plugin, () -> {
                new BukkitRunnable() {
                    int ringIndex = 0;
                    @Override
                    public void run() {
                        if (ringIndex >= rings.size()) {
                            cancel();
                            if (onComplete != null) {
                                new BukkitRunnable() { public void run() { onComplete.run(); } }
                                        .runTaskLater(plugin, 20L);
                            }
                            return;
                        }

                        List<int[]> ring = rings.get(ringIndex);
                        int fallingBlockCount = 0;
                        final int MAX_FALLING_PER_TICK = 80;
                        for (int[] pos : ring) {
                            boolean topFound = false;
                            for (int y = 100; y >= 0; y--) {
                                Block block = world.getBlockAt(pos[0], y, pos[1]);
                                if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                                    Material mat = block.getType();
                                    block.setType(Material.AIR);
                                    // Solo el bloque superior se convierte en FallingBlock (visual)
                                    if (!topFound && fallingBlockCount < MAX_FALLING_PER_TICK) {
                                        topFound = true;
                                        fallingBlockCount++;
                                        FallingBlock fb = world.spawnFallingBlock(
                                                block.getLocation().add(0.5, 0, 0.5), mat.createBlockData());
                                        fb.setDropItem(false);
                                        fb.setHurtEntities(false);
                                        fb.setVelocity(fb.getVelocity().setY(-0.3 - Math.random() * 0.5));
                                    }
                                }
                            }
                        }

                        double currentR = ringIndex * 2.0;
                        plugin.getVoidEffects().spawnCrackParticles(center, currentR);
                        ringIndex++;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            });
        });
    }

    /**
     * Regenera la isla del End (para la cinemática final) con animación dramática.
     */
    public void regenerateEndIsland(Location center, double radius, Runnable onComplete) {
        World world = center.getWorld();
        int cx = (int) center.getX();
        int cz = (int) center.getZ();

        // Destello inicial dorado
        org.bukkit.Particle.DustOptions goldFlash = new org.bukkit.Particle.DustOptions(
                org.bukkit.Color.fromRGB(255, 215, 0), 3.0f);
        world.spawnParticle(org.bukkit.Particle.DUST, center.clone().add(0, 2, 0), 100, 3, 3, 3, 0, goldFlash);
        world.spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 2, 0), 60, 2, 4, 2, 0.1);
        world.playSound(center, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.8f);

        // Pre-calcular posiciones asíncronamente
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<List<int[]>> rings = new ArrayList<>();
            for (double r = 0; r < radius; r += 2.0) {
                List<int[]> ringBlocks = new ArrayList<>();
                for (int x = (int) -r; x <= r; x++) {
                    for (int z = (int) -r; z <= r; z++) {
                        double dist = Math.sqrt(x * x + z * z);
                        if (dist >= r - 2.0 && dist < r && dist <= radius && dist >= 5) {
                            ringBlocks.add(new int[]{cx + x, cz + z, 64 - (int)(dist * 0.3)});
                        }
                    }
                }
                if (!ringBlocks.isEmpty()) rings.add(ringBlocks);
            }

            // Aplicar en el hilo principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                new BukkitRunnable() {
                    int ringIndex = 0;
                    @Override
                    public void run() {
                        if (ringIndex >= rings.size()) {
                            cancel();
                            // Efecto final: explosión de luz
                            world.spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 3, 0),
                                    200, 5, 5, 5, 0.15);
                            world.playSound(center, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
                            if (onComplete != null) {
                                new BukkitRunnable() { public void run() { onComplete.run(); } }
                                        .runTaskLater(plugin, 40L);
                            }
                            return;
                        }

                        List<int[]> ring = rings.get(ringIndex);
                        double currentR = ringIndex * 2.0;

                        for (int[] pos : ring) {
                            int surfaceY = pos[2];
                            // Colocar bloques desde abajo hacia arriba
                            for (int y = surfaceY - 3; y <= surfaceY; y++) {
                                Block b = world.getBlockAt(pos[0], y, pos[1]);
                                if (b.getType() == Material.AIR) {
                                    b.setType(Material.END_STONE);
                                }
                            }

                            // Rayo de luz dorada ascendente por cada bloque colocado
                            Location top = new Location(world, pos[0] + 0.5, surfaceY + 1, pos[1] + 0.5);
                            if (random.nextInt(3) == 0) {
                                world.spawnParticle(org.bukkit.Particle.END_ROD, top,
                                        5, 0.1, 1.5, 0.1, 0.05);
                            }
                            // Destello dorado en la superficie
                            org.bukkit.Particle.DustOptions gold = new org.bukkit.Particle.DustOptions(
                                    org.bukkit.Color.fromRGB(255, 215, 0), 1.5f);
                            world.spawnParticle(org.bukkit.Particle.DUST, top, 2, 0.2, 0.3, 0.2, 0, gold);
                        }

                        // Onda de partículas doradas en el anillo actual
                        int wavePoints = Math.max(20, (int)(currentR * 6));
                        for (int i = 0; i < wavePoints; i++) {
                            double angle = (2 * Math.PI / wavePoints) * i;
                            double wx = center.getX() + currentR * Math.cos(angle);
                            double wz = center.getZ() + currentR * Math.sin(angle);
                            Location wave = new Location(world, wx, center.getY() + 1.5, wz);
                            org.bukkit.Particle.DustOptions waveDust = new org.bukkit.Particle.DustOptions(
                                    org.bukkit.Color.fromRGB(255, 230, 150), 2.0f);
                            world.spawnParticle(org.bukkit.Particle.DUST, wave, 1, 0, 0, 0, 0, waveDust);
                        }

                        // Sonido periódico de reconstrucción
                        if (ringIndex % 4 == 0) {
                            world.playSound(center, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f,
                                    0.7f + (float)(ringIndex) / rings.size() * 1.0f);
                        }
                        if (ringIndex % 8 == 0) {
                            world.playSound(center, org.bukkit.Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.5f);
                        }

                        ringIndex++;
                    }
                }.runTaskTimer(plugin, 20L, 3L);
            });
        });
    }

    /**
     * Destruye torres de obsidiana (cristales explotan)
     */
    public void destroyObsidianTowers(Location center, double searchRadius) {
        World world = center.getWorld();
        // Buscar y eliminar cristales del End
        world.getEntitiesByClass(org.bukkit.entity.EnderCrystal.class).forEach(crystal -> {
            if (crystal.getLocation().distance(center) < searchRadius) {
                plugin.getVoidEffects().bigExplosion(crystal.getLocation());
                crystal.remove();
            }
        });
    }

    /**
     * Teleporta jugadores alrededor de la fuente de forma segura
     */
    public void teleportPlayersToArena(List<Player> players, Location center) {
        double angleStep = (2 * Math.PI) / players.size();
        double radius = 3.0;
        for (int i = 0; i < players.size(); i++) {
            double angle = angleStep * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 1, z,
                    (float) Math.toDegrees(angle + Math.PI), 0);
            Player p = players.get(i);
            p.teleport(loc);
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public Location getArenaCenter() { return arenaCenter; }
    public List<FloatingIsland> getActiveIslands() { return activeIslands; }

    // ======================================================================
    // Clase interna: FloatingIsland
    // ======================================================================

    public class FloatingIsland {
        private final Location center;
        private final int size;
        private int lifetimeTicks;
        private int ticksLived = 0;
        private boolean collapsed = false;
        private final List<Location> blockLocations = new ArrayList<>();

        public FloatingIsland(Location center, int size, int lifetimeTicks) {
            this.center = center;
            this.size = size;
            this.lifetimeTicks = lifetimeTicks;
        }

        public void build() {
            World world = center.getWorld();
            // Plataforma circular de end stone
            for (int x = -size; x <= size; x++) {
                for (int z = -size; z <= size; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= size) {
                        Block b = world.getBlockAt(
                                (int) center.getX() + x, (int) center.getY(), (int) center.getZ() + z);
                        b.setType(Material.END_STONE);
                        blockLocations.add(b.getLocation());

                        // Capa inferior para hacerla más sólida
                        if (dist <= size - 1) {
                            Block below = world.getBlockAt(
                                    (int) center.getX() + x, (int) center.getY() - 1, (int) center.getZ() + z);
                            below.setType(Material.END_STONE);
                            blockLocations.add(below.getLocation());
                        }
                    }
                }
            }
        }

        public void tick() {
            ticksLived += 5; // se llama cada 5 ticks
            // Advertencia visual cuando queda poco tiempo
            if (lifetimeTicks - ticksLived < 60) {
                World world = center.getWorld();
                for (Location loc : blockLocations) {
                    if (random.nextInt(10) < 2) {
                        world.spawnParticle(org.bukkit.Particle.SMOKE,
                                loc.clone().add(0.5, 1, 0.5), 2, 0.3, 0.1, 0.3, 0.02);
                    }
                }
            }
        }

        public void collapse() {
            if (collapsed) return;
            collapsed = true;
            World world = center.getWorld();

            for (Location loc : blockLocations) {
                Block b = world.getBlockAt(loc);
                if (b.getType() != Material.AIR) {
                    Material mat = b.getType();
                    b.setType(Material.AIR);
                    FallingBlock fb = world.spawnFallingBlock(
                            loc.clone().add(0.5, 0, 0.5), mat.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    double vx = (random.nextDouble() - 0.5) * 0.2;
                    double vz = (random.nextDouble() - 0.5) * 0.2;
                    fb.setVelocity(fb.getVelocity().add(
                            new org.bukkit.util.Vector(vx, -0.2, vz)));
                }
            }
            blockLocations.clear();
        }

        public boolean isExpired() { return ticksLived >= lifetimeTicks; }
        public void extendLife(int ticks) { lifetimeTicks += ticks; }
        public Location getCenter() { return center; }
    }
}

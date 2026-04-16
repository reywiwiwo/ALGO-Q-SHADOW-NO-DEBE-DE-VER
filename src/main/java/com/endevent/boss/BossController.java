package com.endevent.boss;

import com.endevent.VoidEventPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Controla la IA de los bosses: 2 Dragones del Vacío + Gigante del Vacío.
 */
public class BossController {

    private final VoidEventPlugin plugin;
    private final Random random = new Random();

    // Entidades de boss
    private EnderDragon blueDragon;
    private EnderDragon purpleDragon;
    private Giant voidGiant;

    // Boss bars
    private BossBar blueDragonBar;
    private BossBar purpleDragonBar;
    private BossBar giantBar;

    // Tareas de IA
    private BukkitTask blueDragonAI;
    private BukkitTask purpleDragonAI;
    private BukkitTask giantAI;
    private BukkitTask combinedAttackTask;

    // Estado
    private boolean dragonsAlive = false;
    private boolean giantAlive = false;
    private Location arenaCenter;
    private List<Player> fighters = new ArrayList<>();

    // Callbacks
    private Runnable onDragonsDead;
    private Runnable onGiantDead;

    public BossController(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    // ==========================================================================
    // FASE DRAGONES
    // ==========================================================================

    /**
     * Spawna los dos dragones del vacío
     */
    public void spawnDragons(Location center, List<Player> players, Runnable onBothDead) {
        this.arenaCenter = center;
        this.fighters = players;
        this.onDragonsDead = onBothDead;
        this.dragonsAlive = true;

        World world = center.getWorld();

        // Dragón Azul — "Glacius, Aliento del Vacío"
        blueDragon = world.spawn(center.clone().add(0, 20, 30), EnderDragon.class, dragon -> {
            dragon.customName(Component.text("Glacius, Aliento del Vacío", NamedTextColor.AQUA));
            dragon.setCustomNameVisible(true);
            dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(400);
            dragon.setHealth(400);
        });

        // Dragón Morado — "Umbra, Sombra del Vacío"
        purpleDragon = world.spawn(center.clone().add(0, 20, -30), EnderDragon.class, dragon -> {
            dragon.customName(Component.text("Umbra, Sombra del Vacío", NamedTextColor.DARK_PURPLE));
            dragon.setCustomNameVisible(true);
            dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(400);
            dragon.setHealth(400);
        });

        // Boss bars
        blueDragonBar = BossBar.bossBar(
                Component.text("Glacius, Aliento del Vacío", NamedTextColor.AQUA),
                1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
        purpleDragonBar = BossBar.bossBar(
                Component.text("Umbra, Sombra del Vacío", NamedTextColor.DARK_PURPLE),
                1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);

        for (Player p : players) {
            p.showBossBar(blueDragonBar);
            p.showBossBar(purpleDragonBar);
        }

        startDragonMovement();
        startBlueDragonAI();
        startPurpleDragonAI();
        startCombinedAttacks();
        startDragonHealthMonitor();
    }

    /** Movimiento continuo: los dragones orbitan el centro a distintas alturas */
    private BukkitTask dragonMovementTask;
    private static final int TRAIL_LENGTH = 12;
    private void startDragonMovement() {
        final LinkedList<Location> blueTrailHistory = new LinkedList<>();
        final LinkedList<Location> purpleTrailHistory = new LinkedList<>();

        dragonMovementTask = new BukkitRunnable() {
            double blueAngle = 0;
            double purpleAngle = Math.PI;
            @Override
            public void run() {
                World world = arenaCenter.getWorld();

                // Dragón Azul — órbita amplia, más alto
                if (blueDragon != null && !blueDragon.isDead()) {
                    blueAngle += 0.03;
                    double radius = 28 + Math.sin(blueAngle * 0.7) * 8;
                    double y = arenaCenter.getY() + 18 + Math.sin(blueAngle * 1.3) * 5;
                    double x = arenaCenter.getX() + radius * Math.cos(blueAngle);
                    double z = arenaCenter.getZ() + radius * Math.sin(blueAngle);
                    Location dest = new Location(world, x, y, z);
                    double nextX = arenaCenter.getX() + radius * Math.cos(blueAngle + 0.05);
                    double nextZ = arenaCenter.getZ() + radius * Math.sin(blueAngle + 0.05);
                    float yaw = (float) Math.toDegrees(Math.atan2(-(nextX - x), (nextZ - z))) + 180;
                    dest.setYaw(yaw);
                    blueDragon.teleport(dest);

                    // Guardar posición en el historial
                    blueTrailHistory.addFirst(dest.clone());
                    if (blueTrailHistory.size() > TRAIL_LENGTH) blueTrailHistory.removeLast();

                    // Rastro azul denso que persiste a lo largo del camino
                    for (int i = 0; i < blueTrailHistory.size(); i++) {
                        Location trailPoint = blueTrailHistory.get(i);
                        float fade = 1.0f - ((float) i / TRAIL_LENGTH);
                        float size = 4.0f * fade + 1.0f;
                        int count = (int) (6 * fade) + 1;
                        Particle.DustOptions outer = new Particle.DustOptions(Color.fromRGB(0, 100, 255), size);
                        world.spawnParticle(Particle.DUST, trailPoint, count, 1.2, 1.2, 1.2, 0, outer);
                        if (i < TRAIL_LENGTH / 2) {
                            Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(120, 220, 255), size * 0.6f);
                            world.spawnParticle(Particle.DUST, trailPoint, count / 2 + 1, 0.6, 0.6, 0.6, 0, core);
                        }
                    }
                    // Partículas especiales en la posición actual
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, dest, 5, 1.0, 0.8, 1.0, 0.03);
                    world.spawnParticle(Particle.END_ROD, dest, 2, 0.5, 0.5, 0.5, 0.02);
                }

                // Dragón Morado — órbita contraria, más bajo
                if (purpleDragon != null && !purpleDragon.isDead()) {
                    purpleAngle -= 0.025;
                    double radius = 25 + Math.cos(purpleAngle * 0.5) * 6;
                    double y = arenaCenter.getY() + 14 + Math.cos(purpleAngle * 1.1) * 4;
                    double x = arenaCenter.getX() + radius * Math.cos(purpleAngle);
                    double z = arenaCenter.getZ() + radius * Math.sin(purpleAngle);
                    Location dest = new Location(world, x, y, z);
                    double nextX = arenaCenter.getX() + radius * Math.cos(purpleAngle - 0.05);
                    double nextZ = arenaCenter.getZ() + radius * Math.sin(purpleAngle - 0.05);
                    float yaw = (float) Math.toDegrees(Math.atan2(-(nextX - x), (nextZ - z))) + 180;
                    dest.setYaw(yaw);
                    purpleDragon.teleport(dest);

                    // Guardar posición en el historial
                    purpleTrailHistory.addFirst(dest.clone());
                    if (purpleTrailHistory.size() > TRAIL_LENGTH) purpleTrailHistory.removeLast();

                    // Rastro morado denso que persiste a lo largo del camino
                    for (int i = 0; i < purpleTrailHistory.size(); i++) {
                        Location trailPoint = purpleTrailHistory.get(i);
                        float fade = 1.0f - ((float) i / TRAIL_LENGTH);
                        float size = 4.0f * fade + 1.0f;
                        int count = (int) (6 * fade) + 1;
                        Particle.DustOptions outer = new Particle.DustOptions(Color.fromRGB(150, 0, 255), size);
                        world.spawnParticle(Particle.DUST, trailPoint, count, 1.2, 1.2, 1.2, 0, outer);
                        if (i < TRAIL_LENGTH / 2) {
                            Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(220, 80, 255), size * 0.6f);
                            world.spawnParticle(Particle.DUST, trailPoint, count / 2 + 1, 0.6, 0.6, 0.6, 0, core);
                        }
                    }
                    // Partículas especiales en la posición actual
                    world.spawnParticle(Particle.DRAGON_BREATH, dest, 5, 1.0, 0.8, 1.0, 0.03, 1.0f);
                    world.spawnParticle(Particle.WITCH, dest, 3, 0.8, 0.8, 0.8, 0.05, null);
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    private void startBlueDragonAI() {
        blueDragonAI = new BukkitRunnable() {
            int tick = 0;
            int attackCooldown = 0;
            @Override
            public void run() {
                if (blueDragon == null || blueDragon.isDead()) { cancel(); return; }
                tick++;
                attackCooldown--;

                updateBossBar(blueDragonBar, blueDragon, 400);

                if (attackCooldown <= 0) {
                    int attack = random.nextInt(10);
                    switch (attack) {
                        case 0 -> blueAttackFrostBreath();
                        case 1 -> blueAttackIceShards();
                        case 2 -> blueAttackFreezeAura();
                        case 3 -> blueAttackVoidPull();
                        case 4 -> blueAttackIceBarrage();
                        case 5 -> blueAttackVoidDive();
                        case 6 -> blueAttackCrystalPrison();
                        case 7 -> blueAttackFrostNova();
                        case 8 -> blueAttackGlacialWall();
                        case 9 -> blueAttackPermafrostField();
                    }
                    attackCooldown = 30 + random.nextInt(40); // 1.5-3.5 segundos
                }
            }
        }.runTaskTimer(plugin, 40L, 5L);
    }

    // --- Ataques Dragón Azul ---

    /** Aliento congelante: cono de partículas azules que aplica slowness */
    private void blueAttackFrostBreath() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Location dragonLoc = blueDragon.getLocation();
        Player target = getNearestPlayer(dragonLoc);
        if (target == null) return;

        Vector dir = target.getLocation().toVector().subtract(dragonLoc.toVector()).normalize();
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 20 || blueDragon == null || blueDragon.isDead()) { cancel(); return; }
                Location start = blueDragon.getLocation().add(0, -2, 0);
                for (int i = 0; i < 15; i++) {
                    double spread = ticks * 0.15;
                    Vector offset = dir.clone().multiply(i * 1.5)
                            .add(new Vector(
                                    (random.nextDouble() - 0.5) * spread,
                                    (random.nextDouble() - 0.5) * spread,
                                    (random.nextDouble() - 0.5) * spread));
                    Location p = start.clone().add(offset);
                    Particle.DustOptions ice = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.5f);
                    start.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, ice);
                }
                // Daño a jugadores en el cono
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && isInCone(start, dir, p.getLocation(), 20, 60)) {
                        p.damage(6.0, blueDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Fragmentos de hielo: proyectiles que caen del cielo */
    private void blueAttackIceShards() {
        if (blueDragon == null || blueDragon.isDead()) return;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 8 || blueDragon == null || blueDragon.isDead()) { cancel(); return; }
                Player target = getRandomPlayer();
                if (target == null) return;
                Location above = target.getLocation().add(
                        (random.nextDouble() - 0.5) * 6, 15, (random.nextDouble() - 0.5) * 6);
                Snowball shard = above.getWorld().spawn(above, Snowball.class);
                shard.setVelocity(new Vector(0, -1.5, 0));
                shard.setCustomName("IceShard");

                // Partículas visuales
                above.getWorld().spawnParticle(Particle.SNOWFLAKE, above, 10, 0.5, 0.5, 0.5, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** Aura congelante: área circular que aplica slowness */
    private void blueAttackFreezeAura() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Location center = blueDragon.getLocation().add(0, -5, 0);
        new BukkitRunnable() {
            int ticks = 0;
            double radius = 3;
            @Override
            public void run() {
                if (ticks++ >= 40) { cancel(); return; }
                radius += 0.3;
                World world = center.getWorld();
                for (int i = 0; i < 30; i++) {
                    double angle = (2 * Math.PI / 30) * i;
                    Location p = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.2f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0.1, 0, 0, dust);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(center) < radius) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
                        p.damage(3.0, blueDragon);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Bombardeo de hielo: lluvia masiva de hielo en área */
    private void blueAttackIceBarrage() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;
        Location center = target.getLocation().clone();
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 15 || blueDragon == null || blueDragon.isDead()) { cancel(); return; }
                for (int i = 0; i < 3; i++) {
                    Location above = center.clone().add(
                            (random.nextDouble() - 0.5) * 12, 20, (random.nextDouble() - 0.5) * 12);
                    Snowball shard = above.getWorld().spawn(above, Snowball.class);
                    shard.setVelocity(new Vector(
                            (random.nextDouble() - 0.5) * 0.3, -2.0, (random.nextDouble() - 0.5) * 0.3));
                    above.getWorld().spawnParticle(Particle.SNOWFLAKE, above, 5, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Picado del vacío: el dragón baja en picado hacia un jugador */
    private void blueAttackVoidDive() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Player target = getNearestPlayer(blueDragon.getLocation());
        if (target == null) return;
        Location targetLoc = target.getLocation().clone();
        Location startLoc = blueDragon.getLocation().clone();
        // Partículas de aviso en el suelo
        new BukkitRunnable() {
            int warn = 0;
            @Override
            public void run() {
                if (warn++ >= 20) { cancel(); return; }
                World world = targetLoc.getWorld();
                for (int i = 0; i < 20; i++) {
                    double angle = (2 * Math.PI / 20) * i;
                    Location p = targetLoc.clone().add(3 * Math.cos(angle), 0.1, 3 * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.5f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        // Impacto después del aviso
        new BukkitRunnable() {
            @Override
            public void run() {
                if (blueDragon == null || blueDragon.isDead()) return;
                targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.5f);
                targetLoc.getWorld().spawnParticle(Particle.EXPLOSION, targetLoc, 3, 1, 1, 1, 0);
                Particle.DustOptions ice = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 3.0f);
                targetLoc.getWorld().spawnParticle(Particle.DUST, targetLoc, 30, 3, 1, 3, 0, ice);
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(targetLoc) < 4) {
                        p.damage(12.0, blueDragon);
                        p.setVelocity(new Vector(0, 0.8, 0));
                    }
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    /** Prisión de cristal: encapsula a un jugador en hielo — blindness + mining fatigue + slowness */
    private void blueAttackCrystalPrison() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;
        Location prisonLoc = target.getLocation().clone();
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 5));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 2));
        target.playSound(prisonLoc, Sound.BLOCK_GLASS_PLACE, 2.0f, 0.3f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 40) { cancel(); return; }
                World world = prisonLoc.getWorld();
                // Jaula giratoria de partículas de hielo
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI / 16) * i + ticks * 0.2;
                    double y = prisonLoc.getY() + (ticks % 20) * 0.1;
                    Location p = prisonLoc.clone().add(1.5 * Math.cos(angle), y - prisonLoc.getY(), 1.5 * Math.sin(angle));
                    Particle.DustOptions ice = new Particle.DustOptions(Color.fromRGB(180, 230, 255), 1.3f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, ice);
                }
                // Columnas verticales de la prisión
                for (int j = 0; j < 4; j++) {
                    double angle = (Math.PI / 2) * j + ticks * 0.1;
                    for (double h = 0; h < 2.5; h += 0.4) {
                        Location p = prisonLoc.clone().add(1.2 * Math.cos(angle), h, 1.2 * Math.sin(angle));
                        world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Nova de escarcha: explosión circular que empuja y congela */
    private void blueAttackFrostNova() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Location center = blueDragon.getLocation().add(0, -4, 0);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 2.0f, 0.3f);

        new BukkitRunnable() {
            int ticks = 0;
            double radius = 0;
            @Override
            public void run() {
                if (ticks++ >= 30) { cancel(); return; }
                radius += 1.2;
                World world = center.getWorld();
                // Anillo de hielo expandiéndose rápidamente
                for (int i = 0; i < 36; i++) {
                    double angle = (2 * Math.PI / 36) * i;
                    Location p = center.clone().add(radius * Math.cos(angle), 0.2, radius * Math.sin(angle));
                    Particle.DustOptions ice = new Particle.DustOptions(Color.fromRGB(200, 240, 255), 2.5f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0.1, 0, 0, ice);
                    world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0.1, 0.1, 0.1, 0.02);
                }
                // Empujar y dañar jugadores en el frente de onda
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    double dist = p.getLocation().distance(center);
                    if (dist >= radius - 2 && dist <= radius + 1) {
                        Vector push = safeNormalize(p.getLocation().toVector().subtract(center.toVector())).multiply(1.2);
                        push.setY(0.4);
                        p.setVelocity(push);
                        p.damage(6.0, blueDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Muro glacial: pared de hielo que avanza hacia los jugadores */
    private void blueAttackGlacialWall() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Player target = getNearestPlayer(blueDragon.getLocation());
        if (target == null) return;
        // Dirección del dragón hacia el jugador
        Location origin = blueDragon.getLocation().add(0, -5, 0);
        Vector dir = safeNormalize(target.getLocation().toVector().subtract(origin.toVector()));
        // Perpendicular para generar la pared
        Vector perp = safeNormalize(new Vector(-dir.getZ(), 0, dir.getX()));
        origin.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.2f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 35) { cancel(); return; }
                World world = origin.getWorld();
                Location wallCenter = origin.clone().add(dir.clone().multiply(ticks * 1.5));
                // Dibujar pared de 10 bloques de ancho × 5 de alto
                for (double w = -5; w <= 5; w += 0.8) {
                    for (double h = 0; h < 5; h += 0.8) {
                        Location p = wallCenter.clone().add(perp.clone().multiply(w)).add(0, h, 0);
                        Particle.DustOptions ice = new Particle.DustOptions(
                                Color.fromRGB(150 + (int)(h * 20), 220, 255), 2.0f);
                        world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, ice);
                    }
                }
                // Daño a jugadores que toca la pared
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    Location pLoc = p.getLocation();
                    // Comprobar si está en el plano de la pared
                    double along = pLoc.toVector().subtract(wallCenter.toVector()).dot(dir);
                    double across = pLoc.toVector().subtract(wallCenter.toVector()).dot(perp);
                    if (Math.abs(along) < 1.5 && Math.abs(across) < 6 && pLoc.getY() < wallCenter.getY() + 5) {
                        p.damage(8.0, blueDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4));
                        p.setVelocity(dir.clone().multiply(0.8).setY(0.3));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Campo de permafrost: el suelo se congela y daña a quien se quede quieto */
    private void blueAttackPermafrostField() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Location center = arenaCenter.clone();
        center.getWorld().playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, 2.0f, 0.5f);
        // Almacenar posiciones de cada jugador
        Map<Player, Location> lastPositions = new HashMap<>();
        for (Player p : fighters) {
            if (p.isOnline() && !p.isDead()) lastPositions.put(p, p.getLocation().clone());
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 80) { cancel(); return; }
                World world = center.getWorld();
                // Partículas de suelo congelado
                for (int i = 0; i < 20; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double r = random.nextDouble() * 15;
                    Location p = center.clone().add(r * Math.cos(angle), 0.1, r * Math.sin(angle));
                    world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0.3, 0.05, 0.3, 0);
                }
                // Jugadores que no se mueven reciben daño creciente
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    Location last = lastPositions.get(p);
                    if (last != null && p.getLocation().distance(last) < 1.0) {
                        p.damage(2.0 + ticks * 0.05, blueDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1));
                        Particle.DustOptions frost = new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.0f);
                        world.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0, frost);
                    }
                    lastPositions.put(p, p.getLocation().clone());
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Tirón del vacío: atrae jugadores hacia el dragón */
    private void blueAttackVoidPull() {
        if (blueDragon == null || blueDragon.isDead()) return;
        Location dragonLoc = blueDragon.getLocation();
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 30 || blueDragon == null || blueDragon.isDead()) { cancel(); return; }
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    Vector pull = safeNormalize(dragonLoc.toVector().subtract(p.getLocation().toVector())).multiply(0.4);
                    p.setVelocity(p.getVelocity().add(pull));
                    p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0),
                            5, 0.3, 0.3, 0.3, 0.5);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // --- IA Dragón Morado ---

    private void startPurpleDragonAI() {
        purpleDragonAI = new BukkitRunnable() {
            int tick = 0;
            int attackCooldown = 0;
            @Override
            public void run() {
                if (purpleDragon == null || purpleDragon.isDead()) { cancel(); return; }
                tick++;
                attackCooldown--;

                updateBossBar(purpleDragonBar, purpleDragon, 400);

                if (attackCooldown <= 0) {
                    int attack = random.nextInt(10);
                    switch (attack) {
                        case 0 -> purpleAttackVoidBreath();
                        case 1 -> purpleAttackTeleportChaos();
                        case 2 -> purpleAttackGravityWell();
                        case 3 -> purpleAttackShadowBolts();
                        case 4 -> purpleAttackShadowVortex();
                        case 5 -> purpleAttackWitherCurse();
                        case 6 -> purpleAttackSoulHarvest();
                        case 7 -> purpleAttackPhantomSwarm();
                        case 8 -> purpleAttackVoidRift();
                        case 9 -> purpleAttackEntropyField();
                    }
                    attackCooldown = 30 + random.nextInt(40);
                }
            }
        }.runTaskTimer(plugin, 60L, 5L);
    }

    // --- Ataques Dragón Morado ---

    /** Aliento del vacío: partículas moradas, daña y ciega */
    private void purpleAttackVoidBreath() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Location dragonLoc = purpleDragon.getLocation();
        Player target = getNearestPlayer(dragonLoc);
        if (target == null) return;

        Vector dir = target.getLocation().toVector().subtract(dragonLoc.toVector()).normalize();
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 20 || purpleDragon == null || purpleDragon.isDead()) { cancel(); return; }
                Location start = purpleDragon.getLocation().add(0, -2, 0);
                for (int i = 0; i < 15; i++) {
                    double spread = ticks * 0.15;
                    Vector offset = dir.clone().multiply(i * 1.5)
                            .add(new Vector(
                                    (random.nextDouble() - 0.5) * spread,
                                    (random.nextDouble() - 0.5) * spread,
                                    (random.nextDouble() - 0.5) * spread));
                    Location p = start.clone().add(offset);
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(100, 0, 180), 1.5f);
                    start.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                    start.getWorld().spawnParticle(Particle.DRAGON_BREATH, p, 1, 0, 0, 0, 0.01, 1.0f);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && isInCone(start, dir, p.getLocation(), 20, 60)) {
                        p.damage(7.0, purpleDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Caos de teleportación: teleporta jugadores a posiciones aleatorias */
    private void purpleAttackTeleportChaos() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        for (Player p : fighters) {
            if (!p.isOnline() || p.isDead() || random.nextInt(3) != 0) continue;
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 5 + random.nextDouble() * 15;
            Location dest = arenaCenter.clone().add(radius * Math.cos(angle), 2, radius * Math.sin(angle));
            // Asegurar que hay un bloque debajo
            dest.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 30, 0.5, 1, 0.5, 1);
            p.teleport(dest);
            dest.getWorld().spawnParticle(Particle.PORTAL, dest, 30, 0.5, 1, 0.5, 1);
            p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 0.5f);
        }
    }

    /** Pozo de gravedad: zona que aplica levitación invertida y daña */
    private void purpleAttackGravityWell() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;
        Location wellCenter = target.getLocation().clone();

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60) { cancel(); return; }
                double radius = 5;
                World world = wellCenter.getWorld();
                for (int i = 0; i < 20; i++) {
                    double angle = (2 * Math.PI / 20) * i + ticks * 0.1;
                    Location p = wellCenter.clone().add(radius * Math.cos(angle), ticks * 0.05, radius * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(50, 0, 100), 2.0f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(wellCenter) < radius) {
                        p.setVelocity(p.getVelocity().add(new Vector(0, -0.3, 0)));
                        if (ticks % 10 == 0) p.damage(2.0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Cosecha de almas: rayo que drena vida del jugador y cura al dragón */
    private void purpleAttackSoulHarvest() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Player target = getNearestPlayer(purpleDragon.getLocation());
        if (target == null) return;
        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.5f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 40 || purpleDragon == null || purpleDragon.isDead()
                        || !target.isOnline() || target.isDead()) { cancel(); return; }
                Location from = purpleDragon.getLocation().add(0, -2, 0);
                Location to = target.getLocation().add(0, 1, 0);
                // Rayo verde-morado de absorción
                plugin.getVoidEffects().drawBeam(from, to, Particle.SOUL, 20);
                // Drenar vida
                if (ticks % 5 == 0) {
                    double drain = 3.0;
                    target.damage(drain, purpleDragon);
                    // Curar al dragón
                    double newHealth = Math.min(400, purpleDragon.getHealth() + drain * 0.5);
                    purpleDragon.setHealth(newHealth);
                    // Partículas de absorción en el jugador
                    target.getWorld().spawnParticle(Particle.SOUL, to, 5, 0.3, 0.3, 0.3, 0.05);
                }
                // Cadenas de partículas que atan al jugador
                for (int i = 0; i < 8; i++) {
                    double angle = (2 * Math.PI / 8) * i + ticks * 0.3;
                    Location chain = to.clone().add(
                            1.5 * Math.cos(angle), Math.sin(ticks * 0.2) * 0.5, 1.5 * Math.sin(angle));
                    Particle.DustOptions soulDust = new Particle.DustOptions(Color.fromRGB(0, 200, 150), 1.0f);
                    target.getWorld().spawnParticle(Particle.DUST, chain, 1, 0, 0, 0, 0, soulDust);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Enjambre de phantoms: invoca phantoms temporales que atacan */
    private void purpleAttackPhantomSwarm() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Location spawnLoc = purpleDragon.getLocation().add(0, -3, 0);
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_PHANTOM_AMBIENT, 2.0f, 0.3f);
        List<Phantom> phantoms = new ArrayList<>();

        // Spawnar 4 phantoms
        for (int i = 0; i < 4; i++) {
            double angle = (2 * Math.PI / 4) * i;
            Location pLoc = spawnLoc.clone().add(3 * Math.cos(angle), 0, 3 * Math.sin(angle));
            pLoc.getWorld().spawnParticle(Particle.PORTAL, pLoc, 15, 0.5, 0.5, 0.5, 0.5);
            Phantom phantom = pLoc.getWorld().spawn(pLoc, Phantom.class, ph -> {
                ph.setSize(2);
                ph.customName(net.kyori.adventure.text.Component.text("Sombra", NamedTextColor.DARK_GRAY));
            });
            phantoms.add(phantom);
        }

        // Los phantoms atacan durante 8 segundos y luego se eliminan
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 160) {
                    for (Phantom ph : phantoms) {
                        if (!ph.isDead()) {
                            ph.getWorld().spawnParticle(Particle.PORTAL, ph.getLocation(), 10, 0.5, 0.5, 0.5, 0.3);
                            ph.remove();
                        }
                    }
                    cancel();
                    return;
                }
                // Partículas de sombra en cada phantom
                for (Phantom ph : phantoms) {
                    if (!ph.isDead()) {
                        Particle.DustOptions shadow = new Particle.DustOptions(Color.fromRGB(40, 0, 60), 1.5f);
                        ph.getWorld().spawnParticle(Particle.DUST, ph.getLocation(), 2, 0.5, 0.3, 0.5, 0, shadow);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Fisura del vacío: grietas en el suelo que erupccionan con daño vertical */
    private void purpleAttackVoidRift() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;
        Location startLoc = target.getLocation().clone();
        Vector riftDir = safeNormalize(new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5));
        startLoc.getWorld().playSound(startLoc, Sound.ENTITY_WARDEN_DIG, 2.0f, 0.5f);

        // Fase 1: La grieta se forma (aviso visual)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 20) { cancel(); return; }
                World world = startLoc.getWorld();
                for (double d = -8; d <= 8; d += 0.5) {
                    Location p = startLoc.clone().add(riftDir.clone().multiply(d));
                    Particle.DustOptions crack = new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.0f);
                    world.spawnParticle(Particle.DUST, p.add(0, 0.1, 0), 1, 0.1, 0, 0.1, 0, crack);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // Fase 2: La grieta erupciona
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 15) { cancel(); return; }
                World world = startLoc.getWorld();
                for (double d = -8; d <= 8; d += 0.8) {
                    Location base = startLoc.clone().add(riftDir.clone().multiply(d));
                    // Columna de partículas que sube
                    for (double h = 0; h < 6; h += 0.5) {
                        Particle.DustOptions void_ = new Particle.DustOptions(Color.fromRGB(100, 0, 180), 2.0f);
                        world.spawnParticle(Particle.DUST, base.clone().add(0, h, 0), 1, 0.15, 0.1, 0.15, 0, void_);
                    }
                    world.spawnParticle(Particle.PORTAL, base, 3, 0.2, 1, 0.2, 0.5);
                }
                // Daño a quien esté sobre la grieta
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    Location pLoc = p.getLocation();
                    double distToLine = distanceToLine(pLoc, startLoc, riftDir);
                    if (distToLine < 2 && Math.abs(projectionLength(pLoc, startLoc, riftDir)) < 9) {
                        p.damage(7.0, purpleDragon);
                        p.setVelocity(new Vector(0, 1.0, 0)); // Lanzar hacia arriba
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 2L);
    }

    /** Campo de entropía: invierte controles — empuja HACIA el peligro */
    private void purpleAttackEntropyField() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Location center = arenaCenter.clone();
        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60) { cancel(); return; }
                World world = center.getWorld();
                // Esfera visual de entropía
                for (int i = 0; i < 30; i++) {
                    double theta = random.nextDouble() * 2 * Math.PI;
                    double phi = random.nextDouble() * Math.PI;
                    double r = 12;
                    Location p = center.clone().add(
                            r * Math.sin(phi) * Math.cos(theta),
                            r * Math.cos(phi) + 5,
                            r * Math.sin(phi) * Math.sin(theta));
                    Particle.DustOptions entropy = new Particle.DustOptions(
                            Color.fromRGB(random.nextInt(100), 0, 100 + random.nextInt(155)), 1.5f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, entropy);
                }
                // Invertir movimiento: empujar jugadores hacia el centro + aplicar nausea
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    if (p.getLocation().distance(center) < 15) {
                        Vector pull = safeNormalize(center.toVector().add(new Vector(0, 3, 0))
                                .subtract(p.getLocation().toVector())).multiply(0.15);
                        p.setVelocity(p.getVelocity().add(pull));
                        if (ticks % 20 == 0) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                            p.damage(3.0, purpleDragon);
                        }
                    }
                }
                // Relámpagos aleatorios dentro de la esfera
                if (ticks % 8 == 0) {
                    Player p = getRandomPlayer();
                    if (p != null) {
                        world.strikeLightningEffect(p.getLocation());
                        p.damage(4.0, purpleDragon);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // Utilidades de geometría para VoidRift
    private double distanceToLine(Location point, Location lineStart, Vector lineDir) {
        Vector v = point.toVector().subtract(lineStart.toVector());
        Vector projection = lineDir.clone().multiply(v.dot(lineDir));
        return v.subtract(projection).length();
    }

    private double projectionLength(Location point, Location lineStart, Vector lineDir) {
        return point.toVector().subtract(lineStart.toVector()).dot(lineDir);
    }

    /** Vórtice de sombras: anillo que se contrae y daña */
    private void purpleAttackShadowVortex() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;
        Location center = target.getLocation().clone();
        new BukkitRunnable() {
            int ticks = 0;
            double radius = 12;
            @Override
            public void run() {
                if (ticks++ >= 50 || radius < 1) { cancel(); return; }
                radius -= 0.22;
                World world = center.getWorld();
                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI / 24) * i + ticks * 0.15;
                    Location p = center.clone().add(radius * Math.cos(angle), 0.2, radius * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.8f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0.2, 0, 0, dust);
                }
                // Daño a los atrapados dentro del anillo
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(center) < radius + 1
                            && p.getLocation().distance(center) > radius - 1) {
                        p.damage(4.0, purpleDragon);
                    }
                }
                // Daño intenso si el anillo llega al centro
                if (radius < 2) {
                    for (Player p : fighters) {
                        if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(center) < 3) {
                            p.damage(8.0, purpleDragon);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 1));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Maldición de wither: aplica wither a jugadores cercanos con partículas */
    private void purpleAttackWitherCurse() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        Location dragonLoc = purpleDragon.getLocation();
        dragonLoc.getWorld().playSound(dragonLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 25 || purpleDragon == null || purpleDragon.isDead()) { cancel(); return; }
                Location current = purpleDragon.getLocation();
                // Onda expansiva de partículas
                double radius = ticks * 0.8;
                World world = current.getWorld();
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI / 16) * i;
                    Location p = current.clone().add(radius * Math.cos(angle), -3, radius * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(30, 30, 30), 2.0f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0.3, 0, 0, dust);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(current) < radius) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 80, 1));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Proyectiles de sombra: bolas de fuego oscuras */
    private void purpleAttackShadowBolts() {
        if (purpleDragon == null || purpleDragon.isDead()) return;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 5 || purpleDragon == null || purpleDragon.isDead()) { cancel(); return; }
                Player target = getRandomPlayer();
                if (target == null) return;
                Location from = purpleDragon.getLocation().add(0, -3, 0);
                Vector dir = safeNormalize(target.getLocation().add(0, 1, 0).toVector()
                        .subtract(from.toVector())).multiply(1.5);
                DragonFireball fb = from.getWorld().spawn(from, DragonFireball.class);
                fb.setDirection(dir);
                fb.setVelocity(dir);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // --- Ataques Combinados ---

    private void startCombinedAttacks() {
        combinedAttackTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!dragonsAlive) { cancel(); return; }
                if (blueDragon == null || blueDragon.isDead() ||
                        purpleDragon == null || purpleDragon.isDead()) {
                    return; // Solo ataques combinados si ambos viven
                }
                tick++;
                if (tick % 20 == 0 && random.nextInt(2) == 0) { // cada ~10s, 50% chance
                    int combo = random.nextInt(6);
                    switch (combo) {
                        case 0 -> combinedVoidStorm();
                        case 1 -> combinedDualBreath();
                        case 2 -> combinedCrossBreath();
                        case 3 -> combinedEclipse();
                        case 4 -> combinedChromaZones();
                        case 5 -> combinedDragonCharge();
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 10L);
    }

    /** Tormenta del vacío: ambos dragones generan un AoE masivo */
    private void combinedVoidStorm() {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 60) { cancel(); return; }
                World world = arenaCenter.getWorld();
                for (int i = 0; i < 30; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double radius = random.nextDouble() * 30;
                    double y = arenaCenter.getY() + random.nextDouble() * 15;
                    Location p = arenaCenter.clone().add(radius * Math.cos(angle), y - arenaCenter.getY(), radius * Math.sin(angle));

                    if (random.nextBoolean()) {
                        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 2.0f);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                    } else {
                        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 0, 255), 2.0f);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                    }
                }
                // Rayos de daño ocasionales
                if (ticks % 10 == 0) {
                    for (Player p : fighters) {
                        if (p.isOnline() && !p.isDead() && random.nextInt(4) == 0) {
                            p.damage(4.0);
                            p.getWorld().strikeLightningEffect(p.getLocation());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Aliento dual: ambos dragones respiran al mismo punto */
    private void combinedDualBreath() {
        Player target = getRandomPlayer();
        if (target == null) return;
        Location targetLoc = target.getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 30) { cancel(); return; }
                if (blueDragon != null && !blueDragon.isDead()) {
                    plugin.getVoidEffects().drawBeam(
                            blueDragon.getLocation().add(0, -2, 0), targetLoc,
                            Particle.SOUL_FIRE_FLAME, 20);
                }
                if (purpleDragon != null && !purpleDragon.isDead()) {
                    plugin.getVoidEffects().drawBeam(
                            purpleDragon.getLocation().add(0, -2, 0), targetLoc,
                            Particle.DRAGON_BREATH, 20, 1.0f);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(targetLoc) < 4) {
                        p.damage(8.0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Aliento cruzado: ambos dragones barren con rayos cruzados */
    private void combinedCrossBreath() {
        if (blueDragon == null || blueDragon.isDead()
                || purpleDragon == null || purpleDragon.isDead()) return;
        new BukkitRunnable() {
            int ticks = 0;
            double sweepAngle = 0;
            @Override
            public void run() {
                if (ticks++ >= 40) { cancel(); return; }
                sweepAngle += 0.08;
                World world = arenaCenter.getWorld();
                // Rayo azul barre de izquierda a derecha
                Location blueFrom = blueDragon.getLocation().add(0, -2, 0);
                Location blueTo = arenaCenter.clone().add(
                        20 * Math.cos(sweepAngle), 0, 20 * Math.sin(sweepAngle));
                plugin.getVoidEffects().drawBeam(blueFrom, blueTo, Particle.SOUL_FIRE_FLAME, 25);
                // Rayo morado barre en sentido contrario
                Location purpleFrom = purpleDragon.getLocation().add(0, -2, 0);
                Location purpleTo = arenaCenter.clone().add(
                        20 * Math.cos(-sweepAngle + Math.PI), 0, 20 * Math.sin(-sweepAngle + Math.PI));
                plugin.getVoidEffects().drawBeam(purpleFrom, purpleTo, Particle.DRAGON_BREATH, 25, 1.0f);
                // Daño en los puntos de impacto
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    if (p.getLocation().distance(blueTo) < 3) {
                        p.damage(5.0, blueDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2));
                    }
                    if (p.getLocation().distance(purpleTo) < 3) {
                        p.damage(5.0, purpleDragon);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Embestida doble: ambos dragones cargan contra un jugador desde lados opuestos */
    private void combinedDragonCharge() {
        if (blueDragon == null || blueDragon.isDead()
                || purpleDragon == null || purpleDragon.isDead()) return;
        Player target = getRandomPlayer();
        if (target == null) return;

        Location targetLoc = target.getLocation().clone();
        Location blueSave = blueDragon.getLocation().clone();
        Location purpleSave = purpleDragon.getLocation().clone();

        // Fase 1: Aviso — líneas de partículas rojas convergen en el jugador
        target.getWorld().playSound(targetLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 1.2f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 25) { cancel(); return; }
                World world = targetLoc.getWorld();
                // Marcador en el suelo
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI / 16) * i + ticks * 0.2;
                    double r = 3 - ticks * 0.08;
                    if (r < 0.5) r = 0.5;
                    Location p = targetLoc.clone().add(r * Math.cos(angle), 0.2, r * Math.sin(angle));
                    Particle.DustOptions warn = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.8f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, warn);
                }
                // Líneas desde cada dragón al objetivo
                if (ticks % 3 == 0) {
                    plugin.getVoidEffects().drawBeam(blueDragon.getLocation(), targetLoc.clone().add(0, 1, 0),
                            Particle.SOUL_FIRE_FLAME, 12);
                    plugin.getVoidEffects().drawBeam(purpleDragon.getLocation(), targetLoc.clone().add(0, 1, 0),
                            Particle.DRAGON_BREATH, 12, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // Fase 2: Embestida — ambos dragones se lanzan hacia el objetivo
        new BukkitRunnable() {
            int ticks = 0;
            final int chargeDuration = 15;
            final Location blueStart = blueDragon.getLocation().clone();
            final Location purpleStart = purpleDragon.getLocation().clone();
            boolean impacted = false;
            @Override
            public void run() {
                if (ticks >= chargeDuration) {
                    if (!impacted) {
                        impacted = true;
                        // Explosión de impacto
                        World world = targetLoc.getWorld();
                        world.playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                        world.playSound(targetLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);
                        plugin.getVoidEffects().bigExplosion(targetLoc);
                        // Onda de choque
                        Particle.DustOptions shockBlue = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 3.0f);
                        Particle.DustOptions shockPurple = new Particle.DustOptions(Color.fromRGB(150, 0, 255), 3.0f);
                        for (int i = 0; i < 30; i++) {
                            double a = (2 * Math.PI / 30) * i;
                            Location ring = targetLoc.clone().add(4 * Math.cos(a), 0.5, 4 * Math.sin(a));
                            world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0, i % 2 == 0 ? shockBlue : shockPurple);
                        }
                        // Daño + knockback a jugadores cercanos
                        for (Player p : fighters) {
                            if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                            double dist = p.getLocation().distance(targetLoc);
                            if (dist < 5) {
                                p.damage(15.0);
                                Vector kb = safeNormalize(p.getLocation().toVector().subtract(targetLoc.toVector()))
                                        .multiply(1.5).setY(0.7);
                                p.setVelocity(kb);
                            } else if (dist < 10) {
                                p.damage(6.0);
                                Vector kb = safeNormalize(p.getLocation().toVector().subtract(targetLoc.toVector()))
                                        .multiply(0.6).setY(0.3);
                                p.setVelocity(kb);
                            }
                        }
                    }
                    // Fase 3: Dragones vuelven a su posición
                    int returnTick = ticks - chargeDuration;
                    int returnDuration = 30;
                    if (returnTick >= returnDuration) {
                        cancel();
                        return;
                    }
                    double t = (double) returnTick / returnDuration;
                    double smooth = t * t * (3 - 2 * t);
                    if (blueDragon != null && !blueDragon.isDead()) {
                        blueDragon.teleport(interpolateLoc(targetLoc, blueSave, smooth));
                    }
                    if (purpleDragon != null && !purpleDragon.isDead()) {
                        purpleDragon.teleport(interpolateLoc(targetLoc, purpleSave, smooth));
                    }
                    ticks++;
                    return;
                }

                double t = (double) ticks / chargeDuration;
                double smooth = t * t * (3 - 2 * t);
                // Mover dragones hacia el objetivo
                if (blueDragon != null && !blueDragon.isDead()) {
                    blueDragon.teleport(interpolateLoc(blueStart, targetLoc, smooth));
                    blueDragon.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            blueDragon.getLocation(), 5, 0.5, 0.5, 0.5, 0.1);
                }
                if (purpleDragon != null && !purpleDragon.isDead()) {
                    purpleDragon.teleport(interpolateLoc(purpleStart, targetLoc, smooth));
                    purpleDragon.getWorld().spawnParticle(Particle.PORTAL,
                            purpleDragon.getLocation(), 8, 0.5, 0.5, 0.5, 0.5);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 50L, 1L);
    }

    private Location interpolateLoc(Location a, Location b, double t) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t,
                a.getYaw() + (b.getYaw() - a.getYaw()) * (float) t,
                a.getPitch());
    }

    /** Eclipse: ambos dragones orbitan sobre el centro creando un vórtice devastador */
    private void combinedEclipse() {
        if (blueDragon == null || blueDragon.isDead()
                || purpleDragon == null || purpleDragon.isDead()) return;
        Location center = arenaCenter.clone().add(0, 3, 0);
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 0.2f);

        new BukkitRunnable() {
            int ticks = 0;
            double vortexRadius = 25;
            @Override
            public void run() {
                if (ticks++ >= 80) { cancel(); return; }
                World world = center.getWorld();
                vortexRadius = Math.max(3, 25 - ticks * 0.28);

                // Partículas del vórtice — espiral descendente
                for (int i = 0; i < 40; i++) {
                    double angle = (2 * Math.PI / 40) * i + ticks * 0.15;
                    double r = vortexRadius * (1 - (double)i / 80);
                    double y = center.getY() + i * 0.3;
                    Location p = center.clone().add(r * Math.cos(angle), y - center.getY(), r * Math.sin(angle));
                    boolean isBlue = i % 2 == 0;
                    Particle.DustOptions dust = new Particle.DustOptions(
                            isBlue ? Color.fromRGB(0, 150, 255) : Color.fromRGB(150, 0, 255), 2.0f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                }

                // Atraer jugadores hacia el centro
                for (Player p : fighters) {
                    if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                    double dist = p.getLocation().distance(center);
                    if (dist < vortexRadius + 5) {
                        Vector pull = safeNormalize(center.toVector().subtract(p.getLocation().toVector()))
                                .multiply(0.3 + (1 - dist / 30) * 0.3);
                        p.setVelocity(p.getVelocity().add(pull));
                        // Daño si está en el ojo del vórtice
                        if (dist < 4 && ticks % 4 == 0) {
                            p.damage(6.0);
                            p.setVelocity(new Vector(0, 0.6, 0));
                        }
                    }
                }

                // Rayos ocasionales
                if (ticks % 12 == 0) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    Location strike = center.clone().add(
                            vortexRadius * 0.5 * Math.cos(angle), 0, vortexRadius * 0.5 * Math.sin(angle));
                    world.strikeLightningEffect(strike);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Zonas cromáticas: zonas azules y moradas alternas — estar en la incorrecta = daño */
    private void combinedChromaZones() {
        if (blueDragon == null || blueDragon.isDead()
                || purpleDragon == null || purpleDragon.isDead()) return;
        Location center = arenaCenter.clone();
        center.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 2.0f, 1.5f);

        // Determinar zonas: dividir en sectores, alternar azul/morado
        // El "color seguro" cambia cada 30 ticks
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 120) { cancel(); return; }
                World world = center.getWorld();
                boolean blueSafe = (ticks / 30) % 2 == 0;

                // Aviso de cambio
                if (ticks % 30 == 1) {
                    String msg = blueSafe ? "§b¡ZONA AZUL SEGURA!" : "§d¡ZONA MORADA SEGURA!";
                    for (Player p : fighters) {
                        if (p.isOnline()) p.sendActionBar(net.kyori.adventure.text.Component.text(
                                blueSafe ? "¡ZONA AZUL SEGURA!" : "¡ZONA MORADA SEGURA!",
                                blueSafe ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE));
                    }
                    world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, blueSafe ? 1.5f : 0.8f);
                }

                // Dibujar zonas
                for (int i = 0; i < 6; i++) {
                    boolean isBlueZone = i % 2 == 0;
                    double startAngle = (Math.PI / 3) * i;
                    double endAngle = startAngle + Math.PI / 3;
                    Color zoneColor = isBlueZone ? Color.fromRGB(0, 100, 255) : Color.fromRGB(150, 0, 200);
                    for (double r = 2; r < 18; r += 2) {
                        for (double a = startAngle; a < endAngle; a += 0.3) {
                            Location p = center.clone().add(r * Math.cos(a), 0.2, r * Math.sin(a));
                            Particle.DustOptions dust = new Particle.DustOptions(zoneColor, 1.5f);
                            world.spawnParticle(Particle.DUST, p, 1, 0.1, 0, 0.1, 0, dust);
                        }
                    }
                }

                // Daño a jugadores en la zona incorrecta
                if (ticks % 5 == 0) {
                    for (Player p : fighters) {
                        if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
                        Location pLoc = p.getLocation();
                        double dx = pLoc.getX() - center.getX();
                        double dz = pLoc.getZ() - center.getZ();
                        double angle = Math.atan2(dz, dx);
                        if (angle < 0) angle += 2 * Math.PI;
                        int sector = (int)(angle / (Math.PI / 3));
                        boolean inBlueZone = sector % 2 == 0;
                        boolean safe = (inBlueZone && blueSafe) || (!inBlueZone && !blueSafe);
                        if (!safe) {
                            p.damage(5.0);
                            Particle.DustOptions warn = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f);
                            world.spawnParticle(Particle.DUST, pLoc.add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0, warn);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void startDragonHealthMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dragonsAlive) { cancel(); return; }
                boolean blueAlive = blueDragon != null && !blueDragon.isDead();
                boolean purpleAlive = purpleDragon != null && !purpleDragon.isDead();

                if (!blueAlive && !purpleAlive) {
                    dragonsAlive = false;
                    cleanupDragons();
                    if (onDragonsDead != null) {
                        new BukkitRunnable() { public void run() { onDragonsDead.run(); } }
                                .runTaskLater(plugin, 40L);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void cleanupDragons() {
        if (blueDragonAI != null) blueDragonAI.cancel();
        if (purpleDragonAI != null) purpleDragonAI.cancel();
        if (combinedAttackTask != null) combinedAttackTask.cancel();
        if (dragonMovementTask != null) dragonMovementTask.cancel();
        for (Player p : fighters) {
            if (p.isOnline()) {
                if (blueDragonBar != null) p.hideBossBar(blueDragonBar);
                if (purpleDragonBar != null) p.hideBossBar(purpleDragonBar);
            }
        }
    }

    // ==========================================================================
    // FASE GIGANTE
    // ==========================================================================

    /**
     * Activa al Gigante como boss final
     */
    public void activateGiant(Location spawnLoc, List<Player> players, Runnable onDead) {
        this.fighters = players;
        this.onGiantDead = onDead;
        this.giantAlive = true;

        World world = spawnLoc.getWorld();
        voidGiant = world.spawn(spawnLoc, Giant.class, giant -> {
            giant.customName(Component.text("El Vacío", NamedTextColor.DARK_GRAY));
            giant.setCustomNameVisible(true);
            giant.getAttribute(Attribute.MAX_HEALTH).setBaseValue(600);
            giant.setHealth(600);
            giant.setGravity(false);
            giant.setAI(false);
            // Hacer al gigante más grande (x3)
            giant.getAttribute(Attribute.SCALE).setBaseValue(3.0);
        });

        giantBar = BossBar.bossBar(
                Component.text("El Vacío - Señor del Abismo", NamedTextColor.DARK_GRAY),
                1.0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_20);
        for (Player p : players) p.showBossBar(giantBar);

        plugin.getVoidEffects().spawnGiantAura(voidGiant);
        startGiantAI();
    }

    private void startGiantAI() {
        giantAI = new BukkitRunnable() {
            int tick = 0;
            int attackCooldown = 0;
            int phase = 1; // 1 = normal, 2 = enraged (< 50% HP)
            @Override
            public void run() {
                if (voidGiant == null || voidGiant.isDead()) {
                    giantAlive = false;
                    cleanupGiant();
                    if (onGiantDead != null) {
                        new BukkitRunnable() { public void run() { onGiantDead.run(); } }
                                .runTaskLater(plugin, 20L);
                    }
                    cancel();
                    return;
                }

                tick++;
                attackCooldown--;
                updateBossBar(giantBar, voidGiant, 600);

                // Movimiento flotante
                moveGiantTowardsPlayers();

                // Cambio de fase
                if (phase == 1 && voidGiant.getHealth() < 300) {
                    phase = 2;
                    plugin.getCinematicEngine().showDialog(
                            "EL VACÍO", "¡INSECTOS! ¡SENTIRÉIS MI VERDADERO PODER!",
                            NamedTextColor.DARK_RED, 60);
                    // Efecto de transición de fase
                    plugin.getVoidEffects().bigExplosion(voidGiant.getLocation());
                }

                if (attackCooldown <= 0) {
                    if (phase == 1) {
                        int attack = random.nextInt(4);
                        switch (attack) {
                            case 0 -> giantAttackVoidSlam();
                            case 1 -> giantAttackDarkOrbs();
                            case 2 -> giantAttackShockwave();
                            case 3 -> giantAttackSummonMinions();
                        }
                        attackCooldown = 50 + random.nextInt(60);
                    } else {
                        int attack = random.nextInt(5);
                        switch (attack) {
                            case 0 -> giantAttackVoidSlam();
                            case 1 -> giantAttackDarkOrbs();
                            case 2 -> giantAttackShockwave();
                            case 3 -> giantAttackSummonMinions();
                            case 4 -> giantAttackVoidBeam();
                        }
                        attackCooldown = 30 + random.nextInt(40); // Más rápido en fase 2
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    /** Mueve al gigante flotando hacia los jugadores */
    private void moveGiantTowardsPlayers() {
        if (voidGiant == null || voidGiant.isDead() || fighters.isEmpty()) return;
        Player nearest = getNearestPlayer(voidGiant.getLocation());
        if (nearest == null) return;

        Location giantLoc = voidGiant.getLocation();
        Vector dir = safeNormalize(nearest.getLocation().toVector().subtract(giantLoc.toVector())).multiply(0.15);
        // Mantener altura flotante
        Location newLoc = giantLoc.add(dir.getX(), 0, dir.getZ());
        newLoc.setY(arenaCenter.getY() + 10);
        voidGiant.teleport(newLoc);
    }

    // --- Ataques del Gigante ---

    /** Golpe del vacío: AoE masivo en el suelo */
    private void giantAttackVoidSlam() {
        if (voidGiant == null || voidGiant.isDead()) return;
        Location target = voidGiant.getLocation().clone();
        target.setY(arenaCenter.getY());

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 3) {
                    // Impacto
                    World world = target.getWorld();
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 5, 3, 1, 3, 0);
                    for (int i = 0; i < 40; i++) {
                        double angle = (2 * Math.PI / 40) * i;
                        for (double r = 0; r < 8; r += 0.5) {
                            Location p = target.clone().add(r * Math.cos(angle), 0.5, r * Math.sin(angle));
                            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(30, 0, 50), 2.0f);
                            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                        }
                    }
                    for (Player p : fighters) {
                        if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(target) < 8) {
                            p.damage(12.0, voidGiant);
                            Vector kb = safeNormalize(p.getLocation().toVector().subtract(target.toVector())).multiply(2).setY(0.8);
                            p.setVelocity(kb);
                        }
                    }
                    cancel();
                    return;
                }
                // Advertencia visual
                World world = target.getWorld();
                for (int i = 0; i < 30; i++) {
                    double angle = (2 * Math.PI / 30) * i;
                    Location p = target.clone().add(8 * Math.cos(angle), 0.2, 8 * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
                    world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
                }
            }
        }.runTaskTimer(plugin, 0L, 15L);
    }

    /** Orbes oscuros: lanza orbes que explotan al llegar */
    private void giantAttackDarkOrbs() {
        if (voidGiant == null || voidGiant.isDead()) return;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 6 || voidGiant == null || voidGiant.isDead()) { cancel(); return; }
                Player target = getRandomPlayer();
                if (target == null) return;
                Location from = voidGiant.getLocation().add(0, 6, 0);
                Location to = target.getLocation();
                Vector dir = safeNormalize(to.toVector().subtract(from.toVector())).multiply(1.2);

                // Orbe visual
                new BukkitRunnable() {
                    Location current = from.clone();
                    int steps = 0;
                    @Override
                    public void run() {
                        if (steps++ >= 40) { explodeOrb(current); cancel(); return; }
                        current.add(dir);
                        World world = current.getWorld();
                        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(20, 0, 40), 2.5f);
                        world.spawnParticle(Particle.DUST, current, 5, 0.2, 0.2, 0.2, 0, dust);
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, current, 2, 0.1, 0.1, 0.1, 0.02);

                        for (Player p : fighters) {
                            if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(current) < 2) {
                                explodeOrb(current);
                                cancel();
                                return;
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    private void explodeOrb(Location loc) {
        World world = loc.getWorld();
        world.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(50, 0, 80), 3.0f);
        world.spawnParticle(Particle.DUST, loc, 20, 1, 1, 1, 0, dust);
        for (Player p : fighters) {
            if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld() && p.getLocation().distance(loc) < 3) {
                p.damage(8.0, voidGiant);
            }
        }
    }

    /** Onda de choque: empuja a todos los jugadores */
    private void giantAttackShockwave() {
        if (voidGiant == null || voidGiant.isDead()) return;
        Location center = voidGiant.getLocation();
        World world = center.getWorld();

        new BukkitRunnable() {
            double radius = 0;
            @Override
            public void run() {
                if (radius >= 20) { cancel(); return; }
                radius += 2;
                for (int i = 0; i < 40; i++) {
                    double angle = (2 * Math.PI / 40) * i;
                    Location p = center.clone().add(radius * Math.cos(angle), 0, radius * Math.sin(angle));
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 0, 120), 2.0f);
                    world.spawnParticle(Particle.DUST, p, 2, 0, 0.5, 0, 0, dust);
                }
                for (Player p : fighters) {
                    if (p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld()) {
                        double dist = p.getLocation().distance(center);
                        if (dist >= radius - 2 && dist < radius + 1) {
                            Vector kb = safeNormalize(p.getLocation().toVector().subtract(center.toVector())).multiply(1.8).setY(0.6);
                            p.setVelocity(kb);
                            p.damage(6.0, voidGiant);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Invoca endermites y vexes */
    private void giantAttackSummonMinions() {
        if (voidGiant == null || voidGiant.isDead()) return;
        Location loc = voidGiant.getLocation();
        World world = loc.getWorld();

        for (int i = 0; i < 4; i++) {
            double angle = (2 * Math.PI / 4) * i;
            Location spawn = loc.clone().add(3 * Math.cos(angle), 0, 3 * Math.sin(angle));
            world.spawn(spawn, Endermite.class, e -> {
                e.customName(Component.text("Parásito del Vacío", NamedTextColor.DARK_GRAY));
            });
        }
        for (int i = 0; i < 2; i++) {
            Location spawn = loc.clone().add(random.nextDouble() * 4 - 2, 2, random.nextDouble() * 4 - 2);
            world.spawn(spawn, Vex.class, v -> {
                v.customName(Component.text("Espíritu del Vacío", NamedTextColor.DARK_PURPLE));
            });
        }
    }

    /** Rayo del vacío: rayo concentrado hacia un jugador (fase 2) */
    private void giantAttackVoidBeam() {
        if (voidGiant == null || voidGiant.isDead()) return;
        Player target = getNearestPlayer(voidGiant.getLocation());
        if (target == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 40 || voidGiant == null || voidGiant.isDead()) { cancel(); return; }
                Location from = voidGiant.getLocation().add(0, 8, 0);
                Location to = target.getLocation().add(0, 1, 0);
                plugin.getVoidEffects().drawDarkBeam(from, to, 30);

                if (target.isOnline() && !target.isDead() && target.getLocation().distance(from) < 25) {
                    if (ticks % 5 == 0) target.damage(5.0, voidGiant);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void cleanupGiant() {
        if (giantAI != null) giantAI.cancel();
        if (giantBar != null) {
            for (Player p : fighters) {
                if (p.isOnline()) p.hideBossBar(giantBar);
            }
        }
    }

    // ==========================================================================
    // GIGANTE PARA CINEMÁTICAS (sin pelea)
    // ==========================================================================

    /**
     * Spawna al gigante para la cinemática (no pelea)
     */
    public Giant spawnCinematicGiant(Location loc, Location lookAt) {
        World world = loc.getWorld();
        // Orientar al gigante mirando hacia la cámara/fuente
        double dx = lookAt.getX() - loc.getX();
        double dz = lookAt.getZ() - loc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        loc.setYaw(yaw);
        return world.spawn(loc, Giant.class, giant -> {
            giant.customName(Component.text("El Vacío", NamedTextColor.DARK_GRAY));
            giant.setCustomNameVisible(true);
            giant.setGravity(false);
            giant.setAI(false);
            giant.setInvulnerable(true);
            // Hacer al gigante más grande (x3)
            giant.getAttribute(Attribute.SCALE).setBaseValue(3.0);
        });
    }

    /**
     * Discurso de muerte del gigante
     */
    public void giantDeathSpeech(Runnable onComplete) {
        plugin.getCinematicEngine().showDialog(
                "EL VACÍO", "No... esto no puede ser...",
                NamedTextColor.DARK_GRAY, 80);

        new BukkitRunnable() { public void run() {
            plugin.getCinematicEngine().showDialog(
                    "EL VACÍO", "¡VOLVERÉ! El vacío es eterno...",
                    NamedTextColor.DARK_RED, 80);
        }}.runTaskLater(plugin, 100L);

        new BukkitRunnable() { public void run() {
            plugin.getCinematicEngine().showDialog(
                    "EL VACÍO", "¡Y cuando lo haga, NO quedará NADA!",
                    NamedTextColor.DARK_RED, 80);
        }}.runTaskLater(plugin, 200L);

        new BukkitRunnable() { public void run() {
            if (voidGiant != null && !voidGiant.isDead()) {
                plugin.getVoidEffects().bigExplosion(voidGiant.getLocation());
                voidGiant.setHealth(0);
            }
            if (onComplete != null) onComplete.run();
        }}.runTaskLater(plugin, 320L);
    }

    // ==========================================================================
    // UTILIDADES
    // ==========================================================================

    private void updateBossBar(BossBar bar, LivingEntity entity, double maxHealth) {
        if (entity == null || entity.isDead()) {
            bar.progress(0);
            return;
        }
        bar.progress(Math.max(0, Math.min(1, (float)(entity.getHealth() / maxHealth))));
    }

    private Player getNearestPlayer(Location loc) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : fighters) {
            if (!p.isOnline() || p.isDead() || p.getWorld() != arenaCenter.getWorld()) continue;
            double dist = p.getLocation().distance(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private Player getRandomPlayer() {
        List<Player> alive = fighters.stream()
                .filter(p -> p.isOnline() && !p.isDead() && p.getWorld() == arenaCenter.getWorld())
                .toList();
        if (alive.isEmpty()) return null;
        return alive.get(random.nextInt(alive.size()));
    }

    private Vector safeNormalize(Vector v) {
        double len = v.length();
        if (len < 0.001) return new Vector(0, 0, 0);
        return v.multiply(1.0 / len);
    }

    private boolean isInCone(Location origin, Vector direction, Location target, double range, double angleDeg) {
        Vector toTarget = target.toVector().subtract(origin.toVector());
        if (toTarget.length() > range) return false;
        double angle = Math.toDegrees(toTarget.angle(direction));
        return angle <= angleDeg;
    }

    public void forceCleanup() {
        cleanupDragons();
        cleanupGiant();
        if (blueDragon != null && !blueDragon.isDead()) blueDragon.remove();
        if (purpleDragon != null && !purpleDragon.isDead()) purpleDragon.remove();
        if (voidGiant != null && !voidGiant.isDead()) voidGiant.remove();
    }

    public Giant getVoidGiant() { return voidGiant; }
    public EnderDragon getBlueDragon() { return blueDragon; }
    public EnderDragon getPurpleDragon() { return purpleDragon; }
    public boolean areDragonsAlive() { return dragonsAlive; }
    public boolean isGiantAlive() { return giantAlive; }
}

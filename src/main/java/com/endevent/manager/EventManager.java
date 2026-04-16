package com.endevent.manager;

import com.endevent.VoidEventPlugin;
import com.endevent.cinematic.CinematicEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Orquesta la secuencia completa del evento (17 pasos).
 * Centro de referencia: la fuente de bedrock del End (0, 64, 0 aprox.)
 */
public class EventManager {

    private final VoidEventPlugin plugin;
    private boolean running = false;

    // Posición de la fuente de bedrock del End
    private Location fountainCenter;
    private World endWorld;

    // Entidades spawneadas para la cinemática
    private final List<ArmorStand> floatingItems = new ArrayList<>();
    private final List<EnderCrystal> endCrystals = new ArrayList<>();
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private ArmorStand dragonEgg;
    private Giant cinematicGiant;
    private EnderDragon cinematicBlueDragon;
    private EnderDragon cinematicPurpleDragon;
    private BukkitTask orbitTask;
    private BukkitTask colorCircleTask;
    private BukkitTask darkBeamTask;
    private BukkitTask crystalBeamTask;
    private BukkitTask battleEffectsTask;
    private BukkitTask platformPunishTask;

    // Items para la animación
    private static final Material[] TOOL_ITEMS = {
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE
    };
    private static final Material[] ARMOR_ITEMS = {
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    };

    public EventManager(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia el evento completo
     */
    public void startEvent(Player initiator) {
        running = true;
        endWorld = initiator.getWorld();
        if (endWorld.getEnvironment() != World.Environment.THE_END) {
            endWorld = plugin.getServer().getWorlds().stream()
                    .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                    .findFirst().orElse(endWorld);
        }

        // Localizar la fuente de bedrock del End
        fountainCenter = findBedRockFountain(endWorld);

        List<Player> allPlayers = new ArrayList<>(endWorld.getPlayers());
        if (!allPlayers.contains(initiator)) allPlayers.add(initiator);

        // Iniciar secuencia cinemática
        plugin.getCinematicEngine().enterCinematicMode(allPlayers);
        phase1_ShowItems();
    }

    // ======================================================================
    // FASE 1: Mostrar objetos en el suelo
    // ======================================================================
    private void phase1_ShowItems() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Spawnar herramientas a un lado de la fuente
        spawnToolsOnGround();
        // Spawnar armadura al otro lado
        spawnArmorOnGround();

        // Centro de herramientas: ~(4.5, 0.5, 0)
        Location toolCenter = fountainCenter.clone().add(4.5, 0.5, 0);
        // TOMA 1: Cámara cercana mirando directamente a las herramientas
        Location shot1From = fountainCenter.clone().add(7, 3, 5);
        lookAt(shot1From, toolCenter);
        Location shot1To = fountainCenter.clone().add(6, 2.5, 2);
        lookAt(shot1To, toolCenter);

        cam.panShot(shot1From, shot1To, 100, () -> { // 5 segundos
            // Centro de armadura: ~(-4.5, 0.5, 0)
            Location armorCenter = fountainCenter.clone().add(-4.5, 0.5, 0);
            // TOMA 2: Cámara cercana mirando directamente a la armadura
            Location shot2From = fountainCenter.clone().add(-7, 3, -5);
            lookAt(shot2From, armorCenter);
            Location shot2To = fountainCenter.clone().add(-6, 2.5, -2);
            lookAt(shot2To, armorCenter);

            cam.panShot(shot2From, shot2To, 100, this::phase2_FloatAndOrbit);
        });
    }

    /** Hace que una Location mire hacia un punto objetivo */
    private void lookAt(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double dz = target.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        from.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        from.setPitch((float) -Math.toDegrees(Math.atan2(dy, dist)));
    }

    // ======================================================================
    // FASE 2: Objetos flotan, huevo aparece, orbitan
    // ======================================================================
    private void phase2_FloatAndOrbit() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Cámara mirando la fuente — transición suave desde la toma anterior
        Location camPos = fountainCenter.clone().add(8, 6, 8);
        lookAt(camPos, fountainCenter.clone().add(0, 2, 0));
        cam.smoothTransition(camPos, 30);

        // Sonido ambiental de inicio
        endWorld.playSound(fountainCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);

        // Spawnar el huevo del dragón en el centro
        Location eggLoc = fountainCenter.clone().add(0, 3, 0);
        dragonEgg = endWorld.spawn(eggLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(false);
            stand.setMarker(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.DRAGON_EGG));
        });

        // Animar los items flotando hacia arriba con partículas
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 40) {
                    cancel();
                    startOrbitAnimation();
                    return;
                }
                for (ArmorStand stand : floatingItems) {
                    Location loc = stand.getLocation();
                    loc.add(0, 0.08, 0);
                    stand.teleport(loc);
                    // Partículas al elevarse
                    endWorld.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0.01);
                    endWorld.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.5);
                }
                // Sonido periódico
                if (tick % 10 == 0) {
                    endWorld.playSound(fountainCenter, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f + tick * 0.02f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 20L, 2L);

        // Spawnar círculo de colores debajo — radio grande (8)
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                colorCircleTask = plugin.getVoidEffects().spawnColorCircle(
                        fountainCenter.clone().add(0, 1.5, 0), 8.0, 80);
            }
        }.runTaskLater(plugin, 40L));
    }

    /** Inicia la animación de órbita: los items giran, partículas giran en sentido contrario */
    private void startOrbitAnimation() {
        orbitTask = new BukkitRunnable() {
            double itemAngle = 0;
            double particleAngle = 0;
            double speed = 0.02;
            double radius = 3.0;
            int tick = 0;

            @Override
            public void run() {
                tick++;
                // Aumentar velocidad y radio gradualmente
                speed = Math.min(0.15, speed + 0.0005);
                radius = Math.min(8.0, radius + 0.008);

                int totalItems = floatingItems.size();
                for (int i = 0; i < totalItems; i++) {
                    ArmorStand stand = floatingItems.get(i);
                    double a = itemAngle + (2 * Math.PI / totalItems) * i;
                    double x = fountainCenter.getX() + radius * Math.cos(a);
                    double z = fountainCenter.getZ() + radius * Math.sin(a);
                    double y = fountainCenter.getY() + 3.5 + Math.sin(tick * 0.05 + i) * 0.3;
                    Location newLoc = new Location(endWorld, x, y, z);
                    stand.teleport(newLoc);
                    // Estela de partículas detrás de cada item
                    endWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, newLoc.clone().add(0, 1, 0), 1, 0.05, 0.05, 0.05, 0.005);
                }
                // Items giran en sentido horario
                itemAngle += speed;

                // Partículas giran en sentido CONTRARIO (antihorario)
                particleAngle -= speed * 1.3;
                int particleCount = 12;
                for (int i = 0; i < particleCount; i++) {
                    double pa = particleAngle + (2 * Math.PI / particleCount) * i;
                    double px = fountainCenter.getX() + (radius + 1) * Math.cos(pa);
                    double pz = fountainCenter.getZ() + (radius + 1) * Math.sin(pa);
                    Location pLoc = new Location(endWorld, px, fountainCenter.getY() + 3.5, pz);
                    Particle.DustOptions dust = new Particle.DustOptions(
                            org.bukkit.Color.fromRGB(128, 0, 255), 1.5f);
                    endWorld.spawnParticle(Particle.DUST, pLoc, 1, 0.05, 0.1, 0.05, 0, dust);
                }

                // Después de ~8 segundos, pasar a fase 3
                if (tick >= 160) {
                    cancel();
                    phase3_StopOrbit();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.add(orbitTask);
    }

    // ======================================================================
    // FASE 3: Los objetos paran de girar bruscamente (vista superior)
    // ======================================================================
    private void phase3_StopOrbit() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Cámara cenital — transición suave desde la órbita
        Location topView = fountainCenter.clone().add(0, 20, 0);
        topView.setPitch(90); // mirando hacia abajo
        cam.smoothTransition(topView, 25);

        // Los items ya están parados (orbitTask cancelado)
        // Pausa dramática de 3 segundos
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                phase4_CrystalBeams();
            }
        }.runTaskLater(plugin, 60L));
    }

    // ======================================================================
    // FASE 4: Los cristales disparan rayos a las herramientas
    // ======================================================================
    private void phase4_CrystalBeams() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Buscar cristales del End existentes o spawnar nuevos
        findOrSpawnCrystals();

        if (endCrystals.isEmpty()) {
            phase5_ReflectedBeams();
            return;
        }

        // TOMA GENERAL: Mostrar la fuente con los cristales alrededor desde arriba
        Location overviewCam = fountainCenter.clone().add(0, 35, 20);
        lookAt(overviewCam, fountainCenter.clone().add(0, 5, 0));
        cam.smoothTransition(overviewCam, 30);

        // Sonido ambiental
        endWorld.playSound(fountainCenter, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.5f);

        // Fase de activación: mostrar cada cristal individualmente con corte de cámara
        crystalBeamTask = new BukkitRunnable() {
            int activated = 0;
            int tick = 0;
            final int ticksPerCrystal = 35; // ~1.75s por cristal
            @Override
            public void run() {
                tick++;

                // Activar un nuevo cristal cuando toque
                if (activated < endCrystals.size() && tick % ticksPerCrystal == 1) {
                    EnderCrystal crystal = endCrystals.get(activated);
                    ArmorStand nearest = findNearestItem(crystal.getLocation());

                    // Corte de cámara: posicionarse cerca del cristal mirando hacia la fuente
                    Location crystalLoc = crystal.getLocation();
                    // Cámara a un lado del cristal, ligeramente elevada
                    double dirX = crystalLoc.getX() - fountainCenter.getX();
                    double dirZ = crystalLoc.getZ() - fountainCenter.getZ();
                    double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
                    // Normalizar y posicionar la cámara al lado del cristal
                    double camX = crystalLoc.getX() + (dirX / len) * 5;
                    double camZ = crystalLoc.getZ() + (dirZ / len) * 5;
                    Location crystalCam = new Location(endWorld, camX, crystalLoc.getY() + 3, camZ);
                    // Mirar hacia la fuente pasando por el cristal
                    lookAt(crystalCam, fountainCenter.clone().add(0, 3, 0));
                    cam.smoothTransition(crystalCam, 8);

                    // Activar beam
                    if (nearest != null) {
                        crystal.setBeamTarget(nearest.getLocation());
                    }
                    endWorld.playSound(crystal.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.0f + activated * 0.1f);
                    // Partículas de activación alrededor del cristal
                    endWorld.spawnParticle(Particle.END_ROD, crystalLoc, 15, 0.5, 0.5, 0.5, 0.1);
                    endWorld.spawnParticle(Particle.FLASH, crystalLoc, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
                    activated++;
                }

                // Actualizar beams activos
                for (int i = 0; i < Math.min(activated, endCrystals.size()); i++) {
                    EnderCrystal crystal = endCrystals.get(i);
                    ArmorStand nearest = findNearestItem(crystal.getLocation());
                    if (nearest != null) {
                        crystal.setBeamTarget(nearest.getLocation());
                        plugin.getVoidEffects().drawCrystalBeam(
                                crystal.getLocation(),
                                nearest.getLocation().add(0, 1, 0), 15);
                    }
                }

                // Cuando todos activados, toma general final y avanzar
                if (activated >= endCrystals.size()) {
                    if (tick == activated * ticksPerCrystal + 5) {
                        // Transición suave a toma general: ver todos los beams
                        Location finalCam = fountainCenter.clone().add(15, 20, 15);
                        lookAt(finalCam, fountainCenter.clone().add(0, 5, 0));
                        cam.smoothTransition(finalCam, 25);
                    }
                    if (tick > activated * ticksPerCrystal + 50) {
                        cancel();
                        phase5_ReflectedBeams();
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 2L);
        activeTasks.add(crystalBeamTask);
    }

    // ======================================================================
    // FASE 5: Rayos reflejados al huevo (negros)
    // ======================================================================
    private void phase5_ReflectedBeams() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Cámara mostrando los objetos y el huevo — transición suave
        Location camPos = fountainCenter.clone().add(6, 6, 6);
        lookAt(camPos, fountainCenter.clone().add(0, 3, 0));
        cam.smoothTransition(camPos, 30);

        // Los items lanzan rayos negros al huevo
        Location eggTarget = dragonEgg != null ? dragonEgg.getLocation() : fountainCenter.clone().add(0, 3, 0);

        darkBeamTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                for (ArmorStand stand : floatingItems) {
                    plugin.getVoidEffects().drawDarkBeam(stand.getLocation().add(0, 1, 0), eggTarget, 20);
                }
                // Partículas de energía acumulándose en el huevo
                endWorld.spawnParticle(Particle.PORTAL, eggTarget, 10, 0.3, 0.3, 0.3, 0.5);
                endWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, eggTarget, 3, 0.2, 0.2, 0.2, 0.02);

                if (tick >= 80) {
                    cancel();
                    phase6_GiantAppears();
                }
            }
        }.runTaskTimer(plugin, 20L, 2L);
        activeTasks.add(darkBeamTask);
    }

    // ======================================================================
    // FASE 6: Aparece el Gigante
    // ======================================================================
    private void phase6_GiantAppears() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Posición final del gigante
        Location giantTarget = fountainCenter.clone().add(60, 15, 0);
        // El gigante empieza MUY lejos/abajo y sube con animación
        Location giantStart = giantTarget.clone().add(20, -30, 0);

        // Cámara mirando al punto de llegada del gigante — transición suave
        Location giantCam = fountainCenter.clone().add(10, 8, 0);
        lookAt(giantCam, giantTarget);
        cam.smoothTransition(giantCam, 35);

        // Efectos de presagio: sonido, partículas oscuras, pantalla sacudiéndose
        endWorld.playSound(fountainCenter, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.3f);
        endWorld.playSound(fountainCenter, Sound.AMBIENT_CAVE, 2.0f, 0.5f);

        // Spawnar gigante en posición inicial (invisible al principio por la distancia)
        cinematicGiant = plugin.getBossController().spawnCinematicGiant(giantStart, fountainCenter);

        // Animación de entrada: el gigante se eleva desde las profundidades
        scheduleTask(new BukkitRunnable() {
            int tick = 0;
            final int duration = 60; // 3 segundos de animación
            @Override
            public void run() {
                if (tick >= duration) {
                    cancel();
                    // Asegurar posición final con yaw mirando a la fuente
                    double finalDx = fountainCenter.getX() - giantTarget.getX();
                    double finalDz = fountainCenter.getZ() - giantTarget.getZ();
                    giantTarget.setYaw((float) Math.toDegrees(Math.atan2(-finalDx, finalDz)));
                    giantTarget.setPitch(0);
                    cinematicGiant.teleport(giantTarget);
                    plugin.getVoidEffects().spawnGiantAura(cinematicGiant);
                    endWorld.playSound(giantTarget, Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.4f);
                    return;
                }
                double t = (double) tick / duration;
                double smooth = t * t * (3 - 2 * t); // smoothstep
                double x = giantStart.getX() + (giantTarget.getX() - giantStart.getX()) * smooth;
                double y = giantStart.getY() + (giantTarget.getY() - giantStart.getY()) * smooth;
                double z = giantStart.getZ() + (giantTarget.getZ() - giantStart.getZ()) * smooth;
                Location current = new Location(endWorld, x, y, z);
                // Recalcular yaw para que siempre mire a la fuente/cámara
                double fdx = fountainCenter.getX() - x;
                double fdz = fountainCenter.getZ() - z;
                current.setYaw((float) Math.toDegrees(Math.atan2(-fdx, fdz)));
                current.setPitch(0);
                cinematicGiant.teleport(current);

                // Partículas oscuras durante la subida
                Particle.DustOptions dark = new Particle.DustOptions(org.bukkit.Color.fromRGB(20, 0, 40), 3.0f);
                endWorld.spawnParticle(Particle.DUST, current.clone().add(0, 6, 0), 5, 3, 4, 3, 0, dark);
                endWorld.spawnParticle(Particle.SMOKE, current.clone().add(0, 6, 0), 3, 2, 3, 2, 0.02);

                tick++;
            }
        }.runTaskTimer(plugin, 20L, 1L));

        // Diálogo (después de que el gigante haya subido)
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                cam.showDialog("EL VACÍO",
                        "¿ENSERIO PENSABAIS QUE OS DEJARÍA",
                        NamedTextColor.DARK_RED, 80);
            }
        }.runTaskLater(plugin, 90L));

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                cam.showDialog("EL VACÍO",
                        "REVIVIR A MI HERMANO USANDO MIS PROPIAS ARMAS?",
                        NamedTextColor.DARK_RED, 80);
            }
        }.runTaskLater(plugin, 180L));

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                phase7_CrystalsExplode();
            }
        }.runTaskLater(plugin, 300L));
    }

    // ======================================================================
    // FASE 7: Cristales explotan, rayos negros continúan
    // ======================================================================
    private void phase7_CrystalsExplode() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Toma general — transición suave
        Location generalShot = fountainCenter.clone().add(0, 30, 25);
        lookAt(generalShot, fountainCenter.clone().add(0, 5, 0));
        cam.smoothTransition(generalShot, 30);

        // Explotar cristales uno a uno
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= endCrystals.size()) {
                    cancel();
                    return;
                }
                EnderCrystal crystal = endCrystals.get(i);
                Location crystalLoc = crystal.getLocation();
                plugin.getVoidEffects().bigExplosion(crystalLoc);
                // Partículas de explosión masivas cuando el gigante destruye los cristales
                crystalLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, crystalLoc, 5, 1, 1, 1, 0);
                Particle.DustOptions shockwave = new Particle.DustOptions(Color.fromRGB(255, 100, 0), 3.5f);
                crystalLoc.getWorld().spawnParticle(Particle.DUST, crystalLoc, 30, 3, 3, 3, 0, shockwave);
                Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(255, 200, 50), 2.5f);
                crystalLoc.getWorld().spawnParticle(Particle.DUST, crystalLoc, 20, 2, 2, 2, 0, glow);
                crystalLoc.getWorld().spawnParticle(Particle.FLAME, crystalLoc, 40, 2, 2, 2, 0.15);
                crystalLoc.getWorld().spawnParticle(Particle.LAVA, crystalLoc, 15, 1.5, 1.5, 1.5, 0);
                crystalLoc.getWorld().playSound(crystalLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                crystalLoc.getWorld().playSound(crystalLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.0f, 0.7f);
                crystal.remove();
                i++;
            }
        }.runTaskTimer(plugin, 10L, 8L);

        // Toma cercana vertical después de las explosiones
        long explosionTime = 10 + (long) endCrystals.size() * 8 + 20;
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                // Toma cercana: los objetos siguen lanzando rayos al huevo
                Location closeShot = fountainCenter.clone().add(3, 7, 3);
                lookAt(closeShot, fountainCenter.clone().add(0, 3, 0));
                cam.smoothTransition(closeShot, 25);

                // Rayos negros siguen activos (el darkBeamTask fue cancelado, reiniciar)
                Location eggRef = dragonEgg != null ? dragonEgg.getLocation() : fountainCenter.clone().add(0, 3, 0);
                darkBeamTask = new BukkitRunnable() {
                    int tick = 0;
                    @Override
                    public void run() {
                        for (ArmorStand stand : floatingItems) {
                            plugin.getVoidEffects().drawDarkBeam(stand.getLocation().add(0, 1, 0), eggRef, 20);
                        }
                        endWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, eggRef, 5, 0.3, 0.3, 0.3, 0.02);
                        tick++;
                        if (tick >= 60) { cancel(); }
                    }
                }.runTaskTimer(plugin, 0L, 2L);
                activeTasks.add(darkBeamTask);
            }
        }.runTaskLater(plugin, explosionTime));

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                phase8_SummonDragons();
            }
        }.runTaskLater(plugin, explosionTime + 130L));
    }

    // ======================================================================
    // FASE 8: Gigante invoca dos dragones
    // ======================================================================
    private void phase8_SummonDragons() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Posición conocida del gigante (misma que phase6)
        Location giantLoc = fountainCenter.clone().add(60, 15, 0);
        Location giantCam = fountainCenter.clone().add(10, 8, 0);
        lookAt(giantCam, giantLoc);
        cam.smoothTransition(giantCam, 35);

        cam.showDialog("EL VACÍO",
                "¡ALZAOS, HIJOS MÍOS!",
                NamedTextColor.DARK_RED, 60);

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                cam.showDialog("EL VACÍO",
                        "¡DEMOSTRAD CÓMO NO SE PUEDE ESCAPAR DEL VACÍO!",
                        NamedTextColor.DARK_RED, 80);
            }
        }.runTaskLater(plugin, 80L));

        // Spawnar dragones con animación de vuelo de entrada
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                // Posiciones finales: a la IZQUIERDA y DERECHA del gigante (eje Z)
                Location blueTarget = giantLoc.clone().add(0, 5, -25);
                Location purpleTarget = giantLoc.clone().add(0, 5, 25);
                // Posiciones iniciales: vienen volando desde lejos y arriba
                Location blueStart = blueTarget.clone().add(-50, 25, -40);
                Location purpleStart = purpleTarget.clone().add(-50, 25, 40);

                // Sonido de aparición
                endWorld.playSound(giantLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.3f);

                cinematicBlueDragon = endWorld.spawn(blueStart, EnderDragon.class, d -> {
                    d.setGravity(false);
                    d.setAI(false);
                    d.setInvulnerable(true);
                    d.customName(net.kyori.adventure.text.Component.text(
                            "Glacius", net.kyori.adventure.text.format.NamedTextColor.AQUA));
                });
                cinematicPurpleDragon = endWorld.spawn(purpleStart, EnderDragon.class, d -> {
                    d.setGravity(false);
                    d.setAI(false);
                    d.setInvulnerable(true);
                    d.customName(net.kyori.adventure.text.Component.text(
                            "Umbra", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
                });

                // Animación: dragones vuelan hacia sus posiciones finales
                new BukkitRunnable() {
                    int tick = 0;
                    final int flyDuration = 50; // 2.5 segundos
                    @Override
                    public void run() {
                        if (tick >= flyDuration) {
                            cancel();
                            cinematicBlueDragon.teleport(blueTarget);
                            cinematicPurpleDragon.teleport(purpleTarget);
                            plugin.getVoidEffects().bigExplosion(blueTarget);
                            plugin.getVoidEffects().bigExplosion(purpleTarget);
                            endWorld.playSound(blueTarget, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
                            endWorld.playSound(purpleTarget, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
                            // Iniciar movimiento idle de dragones
                            startDragonIdleMovement();
                            return;
                        }
                        double t = (double) tick / flyDuration;
                        double smooth = t * t * (3 - 2 * t);

                        // Interpolar posiciones
                        cinematicBlueDragon.teleport(interpolateLoc(blueStart, blueTarget, smooth));
                        cinematicPurpleDragon.teleport(interpolateLoc(purpleStart, purpleTarget, smooth));

                        // Estelas de partículas
                        endWorld.spawnParticle(Particle.END_ROD, cinematicBlueDragon.getLocation(), 3, 1, 1, 1, 0.05);
                        endWorld.spawnParticle(Particle.PORTAL, cinematicPurpleDragon.getLocation(), 5, 1, 1, 1, 0.5);

                        tick++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, 120L));

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                phase9_IslandDestruction();
            }
        }.runTaskLater(plugin, 280L));
    }

    // ======================================================================
    // FASE 9: La isla se destruye
    // ======================================================================
    private void phase9_IslandDestruction() {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Toma general de la isla — transición suave
        Location generalShot = fountainCenter.clone().add(0, 40, 40);
        lookAt(generalShot, fountainCenter);
        cam.smoothTransition(generalShot, 35);

        // Destruir la isla
        plugin.getArenaManager().destroyEndIsland(fountainCenter, 400, () -> {
            // Destruir torres de obsidiana también
            plugin.getArenaManager().destroyObsidianTowers(fountainCenter, 100);

            scheduleTask(new BukkitRunnable() {
                @Override
                public void run() {
                    phase10_StartFight();
                }
            }.runTaskLater(plugin, 60L));
        });
    }

    // ======================================================================
    // FASE 10: Comienza la pelea
    // ======================================================================
    private void phase10_StartFight() {
        // Eliminar entidades cinemáticas
        cleanupCinematicEntities();

        CinematicEngine cam = plugin.getCinematicEngine();
        List<Player> players = new ArrayList<>(cam.getViewers());

        // Salir del modo cinemático
        cam.exitCinematicMode();

        // Teleportar jugadores a la arena
        plugin.getArenaManager().teleportPlayersToArena(players, fountainCenter);

        // Iniciar islas flotantes
        plugin.getArenaManager().startIslandSystem(fountainCenter);

        // Reproducir Pigstep globalmente
        for (Player p : players) {
            if (p.isOnline()) {
                p.playSound(p.getLocation(), Sound.MUSIC_DISC_PIGSTEP, SoundCategory.RECORDS, 1.0f, 1.0f);
            }
        }

        // Efectos de batalla: Super Salto 3 + Caída de Pluma
        startBattleEffects(players);

        // Castigo por quedarse en la plataforma de obsidiana
        startPlatformPunishment(players);

        // Crear una plataforma segura inicial alrededor de la fuente
        buildStartPlatform();

        // Iniciar pelea con los dragones (fase 11-12)
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getBossController().spawnDragons(
                        fountainCenter, players, () -> phase13_GiantFight(players));
            }
        }.runTaskLater(plugin, 40L));
    }

    // ======================================================================
    // FASE 13: Pelea con el Gigante
    // ======================================================================
    private void phase13_GiantFight(List<Player> players) {
        // Diálogo de transición
        plugin.getCinematicEngine().enterCinematicMode(players);
        plugin.getCinematicEngine().showDialog("",
                "Los dragones han caído... pero el verdadero enemigo se acerca.",
                NamedTextColor.GRAY, 60);

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getCinematicEngine().exitCinematicMode();
                for (Player p : players) {
                    p.setGameMode(GameMode.SURVIVAL);
                }

                Location giantSpawn = fountainCenter.clone().add(20, 15, 0);
                plugin.getBossController().activateGiant(giantSpawn, players, () -> {
                    // Fase 14: Discurso de muerte
                    phase14_GiantDeath(players);
                });
            }
        }.runTaskLater(plugin, 80L));
    }

    // ======================================================================
    // FASE 14: Muerte del Gigante
    // ======================================================================
    private void phase14_GiantDeath(List<Player> players) {
        // Entrar en cinemática para el discurso
        plugin.getCinematicEngine().enterCinematicMode(players);

        Giant giant = plugin.getBossController().getVoidGiant();
        if (giant != null && !giant.isDead()) {
            Location giantCam = giant.getLocation().clone().add(-6, 5, 4);
            giantCam.setYaw(-45);
            giantCam.setPitch(10);
            plugin.getCinematicEngine().teleportAll(giantCam);
        }

        plugin.getBossController().giantDeathSpeech(() -> {
            // Detener islas flotantes
            plugin.getArenaManager().stopIslandSystem();
            phase15_ItemsFall(players);
        });
    }

    // ======================================================================
    // FASE 15: Los objetos dejan de disparar y caen
    // ======================================================================
    private void phase15_ItemsFall(List<Player> players) {
        CinematicEngine cam = plugin.getCinematicEngine();

        // Re-crear los items flotantes para la animación final
        spawnFloatingItemsForOutro();
        Location eggLoc = fountainCenter.clone().add(0, 3.5, 0);
        dragonEgg = endWorld.spawn(eggLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.DRAGON_EGG));
        });

        // Cámara mirando la escena — transición suave
        Location camPos = fountainCenter.clone().add(6, 5, 6);
        lookAt(camPos, fountainCenter.clone().add(0, 3, 0));
        cam.smoothTransition(camPos, 30);

        // Los rayos se debilitan y desaparecen
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick < 40) {
                    // Rayos que se debilitan
                    Location eggPos = dragonEgg != null ? dragonEgg.getLocation() : fountainCenter.clone().add(0, 3, 0);
                    for (ArmorStand stand : floatingItems) {
                        if (tick % (2 + tick / 10) == 0) {
                            plugin.getVoidEffects().drawDarkBeam(stand.getLocation().add(0, 1, 0), eggPos, Math.max(3, 20 - tick / 2));
                        }
                    }
                } else if (tick < 80) {
                    // Los items caen al vacío
                    for (ArmorStand stand : floatingItems) {
                        Location loc = stand.getLocation();
                        loc.add(0, -0.15, 0);
                        stand.teleport(loc);
                    }
                } else {
                    // Limpiar items
                    floatingItems.forEach(Entity::remove);
                    floatingItems.clear();
                    cancel();
                    phase16_LightDragonBorn(players);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 20L, 2L);
    }

    // ======================================================================
    // FASE 16: Nace el Dragón de Luz
    // ======================================================================
    private void phase16_LightDragonBorn(List<Player> players) {
        CinematicEngine cam = plugin.getCinematicEngine();
        Location eggLoc = dragonEgg != null ? dragonEgg.getLocation() : fountainCenter.clone().add(0, 3.5, 0);

        // Cámara centrada en el huevo — transición suave
        Location camPos = fountainCenter.clone().add(5, 4, 5);
        lookAt(camPos, eggLoc);
        cam.smoothTransition(camPos, 30);

        // Partículas de muerte del dragón en el huevo
        BukkitTask deathParticles = plugin.getVoidEffects().spawnDragonDeathParticles(eggLoc, 120);

        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                // El huevo "eclosiona"
                if (dragonEgg != null) dragonEgg.remove();
                plugin.getVoidEffects().bigExplosion(eggLoc);

                // Efecto de nacimiento de luz
                plugin.getVoidEffects().spawnLightBirthEffect(eggLoc, 80);

                // Spawnar "Dragón de Luz" (EnderDragon dorado/blanco)
                EnderDragon lightDragon = endWorld.spawn(eggLoc.clone().add(0, 5, 0), EnderDragon.class, d -> {
                    d.setGravity(false);
                    d.setAI(false);
                    d.setInvulnerable(true);
                    d.customName(net.kyori.adventure.text.Component.text(
                            "✦ La Luz ✦", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    d.setCustomNameVisible(true);
                });

                endWorld.playSound(eggLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.2f);

                // Diálogos
                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Al fin... soy libre.",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 40L));

                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Mi hermano, El Vacío, me derrotó hace eones.",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 140L));

                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Me encerró en este huevo, pensando que jamás volvería.",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 240L));

                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Pero vosotros... habéis logrado lo imposible.",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 340L));

                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Aceptad estos regalos como muestra de gratitud.",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 440L));

                // Dar elytras a los jugadores
                scheduleTask(new BukkitRunnable() { public void run() {
                    for (Player p : players) {
                        if (p.isOnline()) {
                            ItemStack elytra = new ItemStack(Material.ELYTRA);
                            ItemMeta meta = elytra.getItemMeta();
                            meta.displayName(Component.text("✦ Alas de la Luz ✦", NamedTextColor.GOLD)
                                    .decorate(TextDecoration.BOLD));
                            meta.addEnchant(Enchantment.UNBREAKING, 5, true);

                            // Tag para identificar las elytras especiales
                            NamespacedKey key = new NamespacedKey(plugin, "alas_de_la_luz");
                            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);

                            // Lore
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.empty());
                            lore.add(Component.text("Forjadas en la última chispa de luz del End,", NamedTextColor.GRAY));
                            lore.add(Component.text("estas alas guardan la memoria de quienes", NamedTextColor.GRAY));
                            lore.add(Component.text("miraron al Vacío a los ojos... y no parpadearon.", NamedTextColor.GRAY));
                            lore.add(Component.empty());
                            lore.add(Component.text("Quien las porte llevará consigo la última", NamedTextColor.WHITE));
                            lore.add(Component.text("voluntad de la Luz: que nadie vuelva a caer.", NamedTextColor.WHITE));
                            lore.add(Component.empty());
                            lore.add(Component.text("✦ Habilidad: Impulso Ascendente", NamedTextColor.YELLOW)
                                    .decorate(TextDecoration.BOLD));
                            lore.add(Component.text("Estando en el suelo, salta dos veces seguidas", NamedTextColor.GRAY));
                            lore.add(Component.text("para recibir un poderoso impulso hacia el cielo.", NamedTextColor.GRAY));
                            lore.add(Component.text("Usa el impulso para tomar vuelo sin necesidad", NamedTextColor.GRAY));
                            lore.add(Component.text("de lanzarte desde las alturas.", NamedTextColor.GRAY));
                            lore.add(Component.empty());
                            meta.lore(lore);

                            elytra.setItemMeta(meta);
                            p.getInventory().addItem(elytra);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        }
                    }
                }}.runTaskLater(plugin, 520L));

                scheduleTask(new BukkitRunnable() { public void run() {
                    cam.showDialog("LA LUZ", "Ahora debo restaurar lo que mi hermano destruyó...",
                            NamedTextColor.GOLD, 80);
                }}.runTaskLater(plugin, 560L));

                // Regenerar la isla
                scheduleTask(new BukkitRunnable() { public void run() {
                    plugin.getArenaManager().regenerateEndIsland(fountainCenter, 400, () -> {
                        phase17_LightDragonLeaves(lightDragon, players);
                    });
                }}.runTaskLater(plugin, 660L));
            }
        }.runTaskLater(plugin, 140L));
    }

    // ======================================================================
    // FASE 17: El Dragón de Luz se va
    // ======================================================================
    private void phase17_LightDragonLeaves(EnderDragon lightDragon, List<Player> players) {
        CinematicEngine cam = plugin.getCinematicEngine();

        cam.showDialog("LA LUZ", "Adiós, héroes. Que la luz os guíe siempre.",
                NamedTextColor.GOLD, 80);

        // Animar al dragón volando hacia arriba
        scheduleTask(new BukkitRunnable() {
            @Override
            public void run() {
                new BukkitRunnable() {
                    int tick = 0;
                    @Override
                    public void run() {
                        if (lightDragon == null || lightDragon.isDead() || tick >= 120) {
                            if (lightDragon != null && !lightDragon.isDead()) lightDragon.remove();
                            cancel();

                            // Terminar evento
                            endEvent(players);
                            return;
                        }
                        Location loc = lightDragon.getLocation();
                        loc.add(0, 0.5, 0);
                        lightDragon.teleport(loc);

                        // Partículas doradas
                        endWorld.spawnParticle(Particle.END_ROD, loc, 5, 1, 1, 1, 0.05);
                        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f);
                        endWorld.spawnParticle(Particle.DUST, loc, 3, 2, 2, 2, 0, gold);
                        tick++;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
        }.runTaskLater(plugin, 100L));
    }

    /**
     * Finaliza el evento
     */
    private void endEvent(List<Player> players) {
        plugin.getCinematicEngine().exitCinematicMode();
        for (Player p : players) {
            if (p.isOnline()) {
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(fountainCenter.clone().add(0, 2, 0));
                // Remover efectos de batalla
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                p.stopSound(Sound.MUSIC_DISC_PIGSTEP, SoundCategory.RECORDS);
            }
        }

        plugin.getCinematicEngine().showDialog("",
                "¡El Evento del Vacío ha terminado!",
                NamedTextColor.GREEN, 100);

        // ── Frases del End Poem ──
        showEndPoemTitles(players);
    }

    private void showEndPoemTitles(List<Player> players) {
        long delay = 120L;

        // ── Acto I: Introspección ──
        delay = poemTitle(players, delay,
                "Veo al jugador al que te refieres.", "",
                NamedTextColor.GRAY, NamedTextColor.GRAY, 3500);
        delay = poemTitle(players, delay,
                "A veces soñaba que era otras cosas,", "en otros lugares.",
                NamedTextColor.GRAY, NamedTextColor.GRAY, 3500);
        delay = poemTitle(players, delay,
                "A veces creía que el universo le hablaba", "a través de los unos y ceros.",
                NamedTextColor.GRAY, NamedTextColor.WHITE, 3500);
        delay = poemTitle(players, delay,
                "A través de las palabras en una pantalla,", "al final de un sueño.",
                NamedTextColor.GRAY, NamedTextColor.WHITE, 4000);
        delay = poemTitle(players, delay,
                "Y el jugador era una historia nueva,", "nunca antes contada, escrita en letras de ADN.",
                NamedTextColor.WHITE, NamedTextColor.GRAY, 4000);
        delay = poemTitle(players, delay,
                "Y el jugador estaba vivo.", "",
                NamedTextColor.WHITE, NamedTextColor.WHITE, 3000);

        // ── Acto II: El universo habla ──
        delay = poemTitle(players, delay,
                "Y el universo dijo", "te quiero.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3000);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "has jugado bien el juego.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3000);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "todo lo que necesitas está dentro de ti.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3500);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "eres más fuerte de lo que crees.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3000);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "eres la luz del día.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3000);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "no estás solo.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 3500);
        delay = poemTitle(players, delay,
                "Y el universo dijo", "todo lo que alguna vez necesitarás, ya lo tienes.",
                NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE, 4000);

        // ── Acto III: Despertar ──
        delay = poemTitle(players, delay,
                "Y el juego terminó", "y el jugador despertó del sueño.",
                NamedTextColor.WHITE, NamedTextColor.GRAY, 4000);
        delay = poemTitle(players, delay,
                "Y el jugador comenzó un nuevo sueño.", "Y soñó de nuevo. Soñó mejor.",
                NamedTextColor.WHITE, NamedTextColor.GRAY, 4000);
        delay = poemTitle(players, delay,
                "Y el jugador era el universo.", "Y el universo era el jugador.",
                NamedTextColor.AQUA, NamedTextColor.WHITE, 4500);
        delay = poemTitle(players, delay,
                "Tú eres el jugador.", "",
                NamedTextColor.GREEN, NamedTextColor.GREEN, 3500);

        // "Despierta." — cierre final
        final long finalDelay = delay;
        scheduleTask(new BukkitRunnable() {
            public void run() {
                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(1000),
                        java.time.Duration.ofMillis(5000),
                        java.time.Duration.ofMillis(2000));
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        Component.text("Despierta.", NamedTextColor.GREEN)
                                .decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC),
                        Component.empty(),
                        times);
                for (Player p : players) {
                    if (p.isOnline()) {
                        p.showTitle(title);
                        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.2f);
                    }
                }
            }
        }.runTaskLater(plugin, finalDelay));
        delay = finalDelay + 160L;

        // Limpiar después de que terminen todos los títulos
        final long cleanupDelay = delay;
        scheduleTask(new BukkitRunnable() {
            public void run() {
                cleanup();
                running = false;
            }
        }.runTaskLater(plugin, cleanupDelay));
    }

    private long poemTitle(List<Player> players, long delay,
                           String top, String bottom,
                           NamedTextColor topColor, NamedTextColor bottomColor,
                           int stayMs) {
        scheduleTask(new BukkitRunnable() {
            public void run() {
                net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(600),
                        java.time.Duration.ofMillis(stayMs),
                        java.time.Duration.ofMillis(800));
                Component titleComp = Component.text(top, topColor).decorate(TextDecoration.ITALIC);
                Component subtitleComp = bottom.isEmpty()
                        ? Component.empty()
                        : Component.text(bottom, bottomColor).decorate(TextDecoration.ITALIC);
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        titleComp, subtitleComp, times);
                for (Player p : players) {
                    if (p.isOnline()) p.showTitle(title);
                }
            }
        }.runTaskLater(plugin, delay));
        return delay + (stayMs / 50) + 28; // stay + fade in/out en ticks
    }

    // ======================================================================
    // UTILIDADES
    // ======================================================================

    /** Interpola linealmente entre dos Locations */
    private Location interpolateLoc(Location a, Location b, double t) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t,
                a.getYaw() + (b.getYaw() - a.getYaw()) * (float) t,
                a.getPitch() + (b.getPitch() - a.getPitch()) * (float) t);
    }

    /** Movimiento idle de los dragones cinemáticos: oscilación suave */
    private void startDragonIdleMovement() {
        if (cinematicBlueDragon == null && cinematicPurpleDragon == null) return;
        final Location blueBase = cinematicBlueDragon != null ? cinematicBlueDragon.getLocation().clone() : null;
        final Location purpleBase = cinematicPurpleDragon != null ? cinematicPurpleDragon.getLocation().clone() : null;

        BukkitTask idleTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                // Movimiento circular suave + bobbing vertical
                double sway = Math.sin(tick * 0.04) * 3;
                double bob = Math.cos(tick * 0.06) * 1.5;
                if (cinematicBlueDragon != null && !cinematicBlueDragon.isDead() && blueBase != null) {
                    cinematicBlueDragon.teleport(blueBase.clone().add(sway, bob, Math.cos(tick * 0.03) * 2));
                }
                if (cinematicPurpleDragon != null && !cinematicPurpleDragon.isDead() && purpleBase != null) {
                    cinematicPurpleDragon.teleport(purpleBase.clone().add(-sway, -bob, -Math.cos(tick * 0.03) * 2));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        activeTasks.add(idleTask);
    }

    /** Busca el ArmorStand (item flotante) más cercano a una ubicación */
    private ArmorStand findNearestItem(Location loc) {
        ArmorStand nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ArmorStand stand : floatingItems) {
            double dist = stand.getLocation().distanceSquared(loc);
            if (dist < minDist) {
                minDist = dist;
                nearest = stand;
            }
        }
        return nearest;
    }

    /**
     * Spawna un ArmorStand completamente invisible con el item en el slot correcto.
     * Herramientas → mano principal (con brazo extendido)
     * Casco → helmet, Peto → chestplate, Pantalones → leggings, Botas → boots
     */
    private ArmorStand spawnItemStand(Location loc, ItemStack item) {
        Material mat = item.getType();
        return endWorld.spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setSmall(false);
            s.setMarker(true);
            s.setBasePlate(false);
            s.setCustomNameVisible(false);
            s.setCanPickupItems(false);
            // Poner el item en su slot correspondiente
            if (mat.name().contains("HELMET")) {
                s.getEquipment().setHelmet(item);
            } else if (mat.name().contains("CHESTPLATE")) {
                s.getEquipment().setChestplate(item);
            } else if (mat.name().contains("LEGGINGS")) {
                s.getEquipment().setLeggings(item);
            } else if (mat.name().contains("BOOTS")) {
                s.getEquipment().setBoots(item);
            } else {
                // Herramientas/espadas → mano principal
                s.setArms(true);
                s.getEquipment().setItemInMainHand(item);
                s.setRightArmPose(new org.bukkit.util.EulerAngle(
                        Math.toRadians(-20), 0, Math.toRadians(-10)));
            }
        });
    }

    private void spawnToolsOnGround() {
        // Herramientas repartidas en semicírculo (+X), separadas ~3 bloques entre sí
        double[][] toolPositions = {{4, 0, 3}, {5.5, 0, 0}, {4, 0, -3}};
        for (int i = 0; i < TOOL_ITEMS.length; i++) {
            ItemStack item = createEnchantedItem(TOOL_ITEMS[i]);
            double[] pos = toolPositions[i];
            Location loc = fountainCenter.clone().add(pos[0], pos[1], pos[2]);
            floatingItems.add(spawnItemStand(loc, item));
        }
    }

    private void spawnArmorOnGround() {
        // Armadura repartida en semicírculo (-X), separadas ~3 bloques entre sí
        double[][] armorPositions = {{-4, 0, 3.5}, {-5.5, 0, 1}, {-5.5, 0, -1}, {-4, 0, -3.5}};
        for (int i = 0; i < ARMOR_ITEMS.length; i++) {
            ItemStack item = createEnchantedItem(ARMOR_ITEMS[i]);
            double[] pos = armorPositions[i];
            Location loc = fountainCenter.clone().add(pos[0], pos[1], pos[2]);
            floatingItems.add(spawnItemStand(loc, item));
        }
    }

    private void spawnFloatingItemsForOutro() {
        floatingItems.clear();
        double radius = 6.0;
        int total = TOOL_ITEMS.length + ARMOR_ITEMS.length;
        Material[] allItems = new Material[total];
        System.arraycopy(TOOL_ITEMS, 0, allItems, 0, TOOL_ITEMS.length);
        System.arraycopy(ARMOR_ITEMS, 0, allItems, TOOL_ITEMS.length, ARMOR_ITEMS.length);

        for (int i = 0; i < total; i++) {
            double angle = (2 * Math.PI / total) * i;
            double x = fountainCenter.getX() + radius * Math.cos(angle);
            double z = fountainCenter.getZ() + radius * Math.sin(angle);
            Location loc = new Location(endWorld, x, fountainCenter.getY() + 4, z);
            ItemStack item = createEnchantedItem(allItems[i]);
            floatingItems.add(spawnItemStand(loc, item));
        }
    }

    private ItemStack createEnchantedItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (material.name().contains("SWORD")) {
                meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            } else if (material.name().contains("PICKAXE") || material.name().contains("AXE")) {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            } else {
                meta.addEnchant(Enchantment.PROTECTION, 4, true);
            }
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void findOrSpawnCrystals() {
        endCrystals.clear();
        // Buscar cristales existentes en el End
        endWorld.getEntitiesByClass(EnderCrystal.class).stream()
                .filter(c -> c.getLocation().distance(fountainCenter) < 100)
                .forEach(endCrystals::add);

        // Si no hay suficientes, crear pilares personalizados y spawnar cristales
        if (endCrystals.size() < 7) {
            endCrystals.clear();
            Random rand = new Random();
            double[][] towerAngles = {{42, 0}, {90, 0}, {138, 0}, {186, 0}, {234, 0}, {282, 0}, {330, 0}};
            for (double[] ta : towerAngles) {
                double angle = Math.toRadians(ta[0]);
                double r = 43;
                double x = fountainCenter.getX() + r * Math.cos(angle);
                double z = fountainCenter.getZ() + r * Math.sin(angle);
                // Construir pilar personalizado
                int baseY = (int) fountainCenter.getY() - 1;
                int pillarHeight = 12 + rand.nextInt(5);
                buildCustomPillar((int) x, baseY, (int) z, pillarHeight);

                Location loc = new Location(endWorld, x, baseY + pillarHeight + 1, z);
                EnderCrystal crystal = endWorld.spawn(loc, EnderCrystal.class, c -> {
                    c.setShowingBottom(false);
                });
                endCrystals.add(crystal);
                // Partículas de creación del pilar
                endWorld.spawnParticle(Particle.END_ROD, loc, 20, 0.5, 1, 0.5, 0.08);
                Particle.DustOptions pillarGlow = new Particle.DustOptions(Color.fromRGB(200, 100, 255), 2.5f);
                endWorld.spawnParticle(Particle.DUST, loc, 15, 1, 1.5, 1, 0, pillarGlow);
            }
        }
    }

    /**
     * Construye un pilar personalizado con obsidiana, crying obsidiana y decoración
     */
    private void buildCustomPillar(int cx, int baseY, int cz, int height) {
        for (int y = baseY; y < baseY + height; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = endWorld.getBlockAt(cx + dx, y, cz + dz);
                    if (Math.abs(dx) + Math.abs(dz) <= 1) {
                        b.setType(Material.OBSIDIAN);
                    } else if (y % 4 == 0) {
                        b.setType(Material.CRYING_OBSIDIAN);
                    }
                }
            }
        }
        // Plataforma superior decorativa
        int topY = baseY + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 2) {
                    Block b = endWorld.getBlockAt(cx + dx, topY, cz + dz);
                    b.setType(dist <= 1 ? Material.OBSIDIAN : Material.END_STONE_BRICKS);
                }
            }
        }
        // Barras de hierro como jaula protectora
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist >= 1.5 && dist <= 2.2) {
                    Block b = endWorld.getBlockAt(cx + dx, topY + 1, cz + dz);
                    b.setType(Material.IRON_BARS);
                }
            }
        }
    }

    private Location findBedRockFountain(World world) {
        // La fuente de bedrock del End siempre está en 0, Y, 0
        for (int y = 100; y >= 50; y--) {
            Block b = world.getBlockAt(0, y, 0);
            if (b.getType() == Material.BEDROCK) {
                return new Location(world, 0, y + 1, 0);
            }
        }
        return new Location(world, 0, 64, 0);
    }

    private void buildStartPlatform() {
        // Plataforma de obsidiana alrededor de la fuente de bedrock
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= 5 && dist > 1) {
                    Block b = endWorld.getBlockAt(
                            (int) fountainCenter.getX() + x,
                            (int) fountainCenter.getY() - 1,
                            (int) fountainCenter.getZ() + z);
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.OBSIDIAN);
                    }
                }
            }
        }
    }

    /**
     * Aplica Super Salto 3 y Caída de Pluma a todos los jugadores durante la batalla
     */
    private void startBattleEffects(List<Player> players) {
        battleEffectsTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                for (Player p : players) {
                    if (p.isOnline() && !p.isDead() && p.getGameMode() == GameMode.SURVIVAL) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2, true, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, true, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
        activeTasks.add(battleEffectsTask);
    }

    /**
     * Castiga a los jugadores que se quedan demasiado tiempo en la plataforma de obsidiana
     */
    private void startPlatformPunishment(List<Player> players) {
        Map<UUID, Integer> timeOnPlatform = new HashMap<>();
        platformPunishTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) { cancel(); return; }
                for (Player p : players) {
                    if (!p.isOnline() || p.isDead()) continue;
                    Location loc = p.getLocation();
                    double dx = loc.getX() - fountainCenter.getX();
                    double dz = loc.getZ() - fountainCenter.getZ();
                    double distH = Math.sqrt(dx * dx + dz * dz);
                    if (distH <= 6 && Math.abs(loc.getY() - fountainCenter.getY()) < 3) {
                        int time = timeOnPlatform.getOrDefault(p.getUniqueId(), 0) + 1;
                        timeOnPlatform.put(p.getUniqueId(), time);
                        if (time > 10) {
                            p.sendActionBar(net.kyori.adventure.text.Component.text(
                                    "¡La oscuridad consume la plataforma! ¡SAL DE AHÍ!",
                                    net.kyori.adventure.text.format.NamedTextColor.RED));
                            p.damage(2.0 + (time - 10) * 0.5);
                            Particle.DustOptions warn = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f);
                            p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0),
                                    5, 0.5, 0.3, 0.5, 0, warn);
                            if (time > 20) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1));
                            }
                        } else if (time > 5) {
                            p.sendActionBar(net.kyori.adventure.text.Component.text(
                                    "¡No te quedes en la plataforma!",
                                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                        }
                    } else {
                        timeOnPlatform.remove(p.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 10L);
        activeTasks.add(platformPunishTask);
    }

    private void cleanupCinematicEntities() {
        // Eliminar entidades cinemáticas (no las del boss fight)
        if (cinematicGiant != null) { cinematicGiant.remove(); cinematicGiant = null; }
        if (cinematicBlueDragon != null) { cinematicBlueDragon.remove(); cinematicBlueDragon = null; }
        if (cinematicPurpleDragon != null) { cinematicPurpleDragon.remove(); cinematicPurpleDragon = null; }
        if (dragonEgg != null) { dragonEgg.remove(); dragonEgg = null; }
        floatingItems.forEach(Entity::remove);
        floatingItems.clear();
        endCrystals.forEach(Entity::remove);
        endCrystals.clear();
        if (colorCircleTask != null && !colorCircleTask.isCancelled()) colorCircleTask.cancel();
    }

    private void scheduleTask(BukkitTask task) {
        activeTasks.add(task);
    }

    public void forceStop() {
        running = false;
        // Remover efectos de batalla de todos los jugadores
        if (endWorld != null) {
            for (Player p : endWorld.getPlayers()) {
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                p.stopSound(Sound.MUSIC_DISC_PIGSTEP, SoundCategory.RECORDS);
            }
        }
        plugin.getCinematicEngine().exitCinematicMode();
        plugin.getArenaManager().stopIslandSystem();
        plugin.getBossController().forceCleanup();
        plugin.getVoidEffects().cancelAll();
        cleanupCinematicEntities();
        for (BukkitTask task : activeTasks) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        activeTasks.clear();
    }

    public void resetEnd(World world) {
        forceStop();
        // Regenerar la isla completa
        Location center = findBedRockFountain(world);
        plugin.getArenaManager().regenerateEndIsland(center, 60, null);
    }

    public boolean isRunning() { return running; }

    public void cleanup() {
        cleanupCinematicEntities();
        plugin.getVoidEffects().cancelAll();
        for (BukkitTask task : activeTasks) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        activeTasks.clear();
    }
}

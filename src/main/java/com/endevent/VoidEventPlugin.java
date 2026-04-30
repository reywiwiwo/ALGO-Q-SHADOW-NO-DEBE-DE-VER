package com.endevent;

import com.endevent.arena.ArenaManager;
import com.endevent.boss.BossController;
import com.endevent.cinematic.CinematicEngine;
import com.endevent.commands.EventCommand;
import com.endevent.effects.VoidEffects;
import com.endevent.listener.ArmorStandVanishTask;
import com.endevent.listener.ProtectionListener;
import com.endevent.manager.EventManager;
import org.bukkit.plugin.java.JavaPlugin;

public class VoidEventPlugin extends JavaPlugin {

    private static VoidEventPlugin instance;
    private EventManager eventManager;
    private CinematicEngine cinematicEngine;
    private ArenaManager arenaManager;
    private BossController bossController;
    private VoidEffects voidEffects;
    private ArmorStandVanishTask armorStandVanishTask;

    @Override
    public void onEnable() {
        instance = this;

        this.voidEffects = new VoidEffects(this);
        this.cinematicEngine = new CinematicEngine(this);
        this.arenaManager = new ArenaManager(this);
        this.bossController = new BossController(this);
        this.eventManager = new EventManager(this);

        getCommand("voidevent").setExecutor(new EventCommand(this));
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new com.endevent.listener.ElytraAbilityListener(this), this);

        this.armorStandVanishTask = new ArmorStandVanishTask(this);

        getLogger().info("VoidEvent cargado — /voidevent start para iniciar");
    }

    @Override
    public void onDisable() {
        if (armorStandVanishTask != null) armorStandVanishTask.stop();
        if (eventManager != null && eventManager.isRunning()) {
            eventManager.forceStop();
        }
        getLogger().info("VoidEvent desactivado");
    }

    public static VoidEventPlugin getInstance() {
        return instance;
    }

    public EventManager getEventManager() { return eventManager; }
    public CinematicEngine getCinematicEngine() { return cinematicEngine; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public BossController getBossController() { return bossController; }
    public VoidEffects getVoidEffects() { return voidEffects; }
}

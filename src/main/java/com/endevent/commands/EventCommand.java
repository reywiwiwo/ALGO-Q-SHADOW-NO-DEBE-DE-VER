package com.endevent.commands;

import com.endevent.VoidEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class EventCommand implements CommandExecutor {

    private final VoidEventPlugin plugin;

    public EventCommand(VoidEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Solo jugadores pueden usar este comando.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Uso: /voidevent <start|stop|reset>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (plugin.getEventManager().isRunning()) {
                    player.sendMessage(Component.text("El evento ya está en curso.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text("Iniciando Evento del Vacío...", NamedTextColor.DARK_PURPLE));
                plugin.getEventManager().startEvent(player);
            }
            case "stop" -> {
                if (!plugin.getEventManager().isRunning()) {
                    player.sendMessage(Component.text("No hay evento en curso.", NamedTextColor.RED));
                    return true;
                }
                plugin.getEventManager().forceStop();
                player.sendMessage(Component.text("Evento detenido.", NamedTextColor.YELLOW));
            }
            case "reset" -> {
                plugin.getEventManager().resetEnd(player.getWorld());
                player.sendMessage(Component.text("End reseteado.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("Uso: /voidevent <start|stop|reset>", NamedTextColor.YELLOW));
        }
        return true;
    }
}

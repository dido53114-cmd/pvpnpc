package fr.exemple.pvpnpc.commands;

import fr.exemple.pvpnpc.Arena;
import fr.exemple.pvpnpc.ArenaManager;
import fr.exemple.pvpnpc.PvpNpcPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArenaCommand implements CommandExecutor {

    private final PvpNpcPlugin plugin;

    public ArenaCommand(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("/arenapvp <pos1|pos2|spawn1|spawn2|create <nom>|remove <nom>|list>", NamedTextColor.YELLOW));
            return true;
        }

        ArenaManager am = plugin.getArenaManager();
        switch (args[0].toLowerCase()) {
            case "pos1" -> {
                am.setPos1(p);
                p.sendMessage(Component.text("Coin 1 defini.", NamedTextColor.GREEN));
            }
            case "pos2" -> {
                am.setPos2(p);
                p.sendMessage(Component.text("Coin 2 defini.", NamedTextColor.GREEN));
            }
            case "spawn1" -> {
                am.setSpawn1(p);
                p.sendMessage(Component.text("Point de spawn du joueur 1 defini.", NamedTextColor.GREEN));
            }
            case "spawn2" -> {
                am.setSpawn2(p);
                p.sendMessage(Component.text("Point de spawn du joueur 2 defini.", NamedTextColor.GREEN));
            }
            case "create" -> {
                if (args.length < 2) {
                    p.sendMessage(Component.text("Usage : /arenapvp create <nom>", NamedTextColor.RED));
                    return true;
                }
                String error = am.createArena(p, args[1]);
                if (error != null) {
                    p.sendMessage(Component.text(error, NamedTextColor.RED));
                } else {
                    p.sendMessage(Component.text("Arene \"" + args[1] + "\" creee !", NamedTextColor.GREEN));
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    p.sendMessage(Component.text("Usage : /arenapvp remove <nom>", NamedTextColor.RED));
                    return true;
                }
                am.removeArena(args[1]);
                p.sendMessage(Component.text("Arene supprimee.", NamedTextColor.GREEN));
            }
            case "list" -> {
                p.sendMessage(Component.text("Arenes : ", NamedTextColor.YELLOW));
                for (Arena a : am.getArenas()) {
                    p.sendMessage(Component.text(" - " + a.getName() + (a.isInUse() ? " (en cours)" : " (libre)"), NamedTextColor.GRAY));
                }
            }
            default -> p.sendMessage(Component.text("Sous-commande inconnue.", NamedTextColor.RED));
        }
        return true;
    }
}

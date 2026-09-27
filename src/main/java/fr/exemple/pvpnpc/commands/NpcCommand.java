package fr.exemple.pvpnpc.commands;

import fr.exemple.pvpnpc.PvpNpcPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NpcCommand implements CommandExecutor {

    private final PvpNpcPlugin plugin;

    public NpcCommand(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande reservee aux joueurs.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Component.text("/npcpvp <create <serveur-cible>|remove>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                String target = args.length >= 2 ? args[1] : plugin.getConfig().getString("npc-target-server", "pvp");
                plugin.getNpcManager().createAndSave(p.getLocation(), target);
                p.sendMessage(Component.text("PNJ cree, il enverra les joueurs vers le serveur \"" + target + "\".", NamedTextColor.GREEN));
            }
            case "remove" -> {
                boolean removed = plugin.getNpcManager().removeNearest(p);
                p.sendMessage(removed
                        ? Component.text("PNJ supprime.", NamedTextColor.GREEN)
                        : Component.text("Aucun PNJ PvP a proximite (5 blocs).", NamedTextColor.RED));
            }
            default -> p.sendMessage(Component.text("Sous-commande inconnue.", NamedTextColor.RED));
        }
        return true;
    }
}

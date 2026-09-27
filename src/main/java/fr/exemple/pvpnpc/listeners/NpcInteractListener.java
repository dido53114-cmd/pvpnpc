package fr.exemple.pvpnpc.listeners;

import fr.exemple.pvpnpc.NpcManager;
import fr.exemple.pvpnpc.PvpNpcPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public class NpcInteractListener implements Listener {

    private final PvpNpcPlugin plugin;

    public NpcInteractListener(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        // On ignore l'evenement duplique de la main secondaire pour ne pas teleporter deux fois
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        NamespacedKey tagKey = new NamespacedKey(plugin, NpcManager.TAG_KEY);
        if (!event.getRightClicked().getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        NamespacedKey targetKey = new NamespacedKey(plugin, NpcManager.TARGET_KEY);
        String targetServer = event.getRightClicked().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (targetServer == null || targetServer.isEmpty()) {
            targetServer = plugin.getConfig().getString("npc-target-server", "pvp");
        }

        player.sendMessage(Component.text("Teleportation vers le serveur PvP...", NamedTextColor.AQUA));
        plugin.sendToServer(player, targetServer);
    }
}

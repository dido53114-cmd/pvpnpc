package fr.exemple.pvpnpc.listeners;

import fr.exemple.pvpnpc.PvpNpcPlugin;
import fr.exemple.pvpnpc.QueueManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class MatchProtectionListener implements Listener {

    private final PvpNpcPlugin plugin;

    public MatchProtectionListener(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Coeur de "l'isolation par parcelle" : deux joueurs ne peuvent se blesser
     * mutuellement que s'ils sont dans le meme combat actif ET que le decompte est termine.
     * Ca permet a plusieurs arenes de tourner en meme temps dans le meme monde sans interference.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        QueueManager qm = plugin.getQueueManager();
        QueueManager.MatchSession victimSession = qm.getMatch(victim.getUniqueId());
        QueueManager.MatchSession attackerSession = qm.getMatch(attacker.getUniqueId());

        boolean sameActiveMatch = victimSession != null
                && victimSession == attackerSession
                && victimSession.pvpAllowed;

        if (!sameActiveMatch) {
            event.setCancelled(true);
        }
    }

    /** Empeche toute source de degats pendant le decompte (le temps que l'invulnerabilite suffise pas partout). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageGeneric(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        QueueManager.MatchSession session = plugin.getQueueManager().getMatch(p.getUniqueId());
        if (session != null && !session.pvpAllowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        QueueManager.MatchSession session = plugin.getQueueManager().getMatch(loser.getUniqueId());
        if (session == null) return;

        UUID winnerUuid = session.getOpponent(loser.getUniqueId());

        // On recupere le stuff AVANT de vider l'inventaire, pour le faire tomber nous-memes au sol.
        ItemStack[] contents = loser.getInventory().getContents().clone();
        event.setCancelled(true); // pas de vraie mort vanilla : on gere nous-memes le respawn et le loot
        loser.getInventory().clear();

        plugin.getQueueManager().concludeMatchOnDeath(session, winnerUuid, loser.getUniqueId(), loser.getLocation(), contents);
    }

    /** Une deconnexion en plein combat = defaite forcee (anti-fuite). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        QueueManager qm = plugin.getQueueManager();
        QueueManager.MatchSession session = qm.getMatch(uuid);
        if (session != null) {
            UUID winnerUuid = session.getOpponent(uuid);
            qm.concludeMatchOnQuit(session, winnerUuid, uuid);
            return;
        }

        if (qm.isLooting(uuid)) {
            qm.notifyQuitDuringLoot(uuid);
            return;
        }

        qm.leaveQueue(p);
    }

    /** Bloque les commandes de fuite (/server, /hub, /tpa...) pendant un combat actif (pas pendant la phase de loot). */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getQueueManager().isInMatch(p.getUniqueId())) return;

        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase();
        List<String> blocked = plugin.getConfig().getStringList("blocked-commands-in-match");
        if (blocked.contains(cmd)) {
            event.setCancelled(true);
            p.sendMessage(Component.text("Tu ne peux pas fuir un combat !", NamedTextColor.RED));
        }
    }

    /** Empeche les teleportations "externes" pendant un combat actif. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (plugin.isInternalTeleport(p)) return; // nos propres teleportations doivent toujours passer

        if (plugin.getQueueManager().isInMatch(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(Component.text("Impossible de se teleporter pendant un combat !", NamedTextColor.RED));
        }
    }

    /**
     * Detecte quand le gagnant, en phase de loot, part de lui-meme (ex: /spawn d'un autre plugin) :
     * on le libere alors immediatement de la phase de loot au lieu d'attendre la fin des 5 minutes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportDuringLoot(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (plugin.getQueueManager().isLooting(p.getUniqueId())) {
            plugin.getQueueManager().notifyLeftLootZone(p.getUniqueId());
        }
    }
}

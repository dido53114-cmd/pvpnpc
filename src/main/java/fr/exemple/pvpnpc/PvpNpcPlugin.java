package fr.exemple.pvpnpc;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.exemple.pvpnpc.commands.ArenaCommand;
import fr.exemple.pvpnpc.commands.NpcCommand;
import fr.exemple.pvpnpc.listeners.MatchProtectionListener;
import fr.exemple.pvpnpc.listeners.NpcInteractListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvpNpcPlugin extends JavaPlugin implements Listener {

    private ArenaManager arenaManager;
    private QueueManager queueManager;
    private NpcManager npcManager;

    // Marque temporairement les joueurs qu'on teleporte nous-memes, pour ne pas se bloquer
    // avec notre propre protection anti-teleportation pendant un combat.
    private final Set<UUID> internalTeleportFlags = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.arenaManager = new ArenaManager(this);
        this.queueManager = new QueueManager(this);
        this.npcManager = new NpcManager(this);
        npcManager.loadAll();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MatchProtectionListener(this), this);

        getCommand("arenapvp").setExecutor(new ArenaCommand(this));
        getCommand("npcpvp").setExecutor(new NpcCommand(this));

        getLogger().info("PvpNpc active (role: " + getConfig().getString("server-role", "HUB") + ")");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    // Si ce serveur est le serveur PvP, on met automatiquement en file d'attente
    // tout joueur qui arrive (c'est la "salle d'attente" demandee).
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if ("PVP".equalsIgnoreCase(getConfig().getString("server-role", "HUB"))) {
            Bukkit.getScheduler().runTaskLater(this, () -> queueManager.joinQueue(event.getPlayer()), 20L);
        }
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    // ---------------------------------------------------------
    // Reseau (BungeeCord/Velocity en mode legacy forwarding)
    // ---------------------------------------------------------

    /** Envoie un joueur vers un autre serveur du reseau via le canal de plugin BungeeCord. */
    public void sendToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    /** Renvoie un joueur au "retour" configure : soit un autre serveur (bungee), soit juste son point d'arrivee local. */
    public void sendPlayerHome(Player player) {
        String returnServer = getConfig().getString("return-server", "");
        if (returnServer != null && !returnServer.isBlank()
                && !returnServer.equalsIgnoreCase(getConfig().getString("server-role"))) {
            sendToServer(player, returnServer);
        } else {
            teleportInternally(player, player.getWorld().getSpawnLocation());
        }
    }

    // ---------------------------------------------------------
    // Teleportations internes (autorisees a travers la protection anti-fuite)
    // ---------------------------------------------------------

    public void teleportInternally(Player player, Location loc) {
        internalTeleportFlags.add(player.getUniqueId());
        player.teleport(loc);
        // On retire le flag au tick suivant : le PlayerTeleportEvent est synchrone donc
        // il aura deja ete traite avant que ce retard ne s'execute.
        Bukkit.getScheduler().runTaskLater(this, () -> internalTeleportFlags.remove(player.getUniqueId()), 2L);
    }

    public boolean isInternalTeleport(Player player) {
        return internalTeleportFlags.contains(player.getUniqueId());
    }
}

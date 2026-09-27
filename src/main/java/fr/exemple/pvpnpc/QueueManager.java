package fr.exemple.pvpnpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.util.*;

public class QueueManager {

    private final PvpNpcPlugin plugin;
    private final LinkedList<UUID> waiting = new LinkedList<>();
    private final Map<UUID, BukkitTask> waitingSoundTasks = new HashMap<>();
    private final Map<UUID, MatchSession> activeMatches = new HashMap<>();

    // Joueurs en phase de "loot" (le combat est fini, ils ont le droit de rester looter le perdant)
    private final Map<UUID, Arena> lootingPlayers = new HashMap<>();
    private final Map<UUID, BukkitTask> lootGraceTasks = new HashMap<>();

    public QueueManager(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Represente un combat 1v1 en cours dans une arene. */
    public static class MatchSession {
        final UUID player1;
        final UUID player2;
        final Arena arena;
        boolean pvpAllowed = false; // passe a true une fois le decompte termine
        BukkitTask shrinkTask;      // tache du retrecissement de zone, annulee a la fin du combat

        MatchSession(UUID p1, UUID p2, Arena arena) {
            this.player1 = p1;
            this.player2 = p2;
            this.arena = arena;
        }

        public UUID getOpponent(UUID of) {
            return of.equals(player1) ? player2 : player1;
        }
    }

    public boolean isInMatch(UUID uuid) {
        return activeMatches.containsKey(uuid);
    }

    public MatchSession getMatch(UUID uuid) {
        return activeMatches.get(uuid);
    }

    public boolean isInQueue(UUID uuid) {
        return waiting.contains(uuid);
    }

    public boolean isLooting(UUID uuid) {
        return lootingPlayers.containsKey(uuid);
    }

    // ---------------------------------------------------------
    // File d'attente
    // ---------------------------------------------------------

    public void joinQueue(Player p) {
        if (isInMatch(p.getUniqueId()) || isInQueue(p.getUniqueId()) || isLooting(p.getUniqueId())) return;
        waiting.add(p.getUniqueId());
        p.sendMessage(Component.text("Tu as rejoint la file d'attente PvP...", NamedTextColor.YELLOW));

        String soundName = plugin.getConfig().getString("queue.waiting-sound", "block.amethyst_block.chime");
        int interval = plugin.getConfig().getInt("queue.sound-interval-seconds", 4) * 20;
        Sound sound = safeSound(soundName);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) return;
            p.sendActionBar(Component.text("En attente d'un adversaire...", NamedTextColor.GOLD));
            if (sound != null) p.playSound(p.getLocation(), sound, 0.6f, 1.2f);
        }, 0L, interval);
        waitingSoundTasks.put(p.getUniqueId(), task);

        tryStartMatch();
    }

    public void leaveQueue(Player p) {
        waiting.remove(p.getUniqueId());
        stopWaitingSound(p.getUniqueId());
    }

    private void stopWaitingSound(UUID uuid) {
        BukkitTask t = waitingSoundTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private void tryStartMatch() {
        int minPlayers = plugin.getConfig().getInt("queue.min-players-to-start", 2);
        if (waiting.size() < minPlayers) return;

        Arena arena = plugin.getArenaManager().getFreeArena();
        if (arena == null) return; // pas d'arene libre pour l'instant, on retente au prochain join/fin de combat

        UUID id1 = waiting.poll();
        UUID id2 = waiting.poll();
        Player p1 = Bukkit.getPlayer(id1);
        Player p2 = Bukkit.getPlayer(id2);

        stopWaitingSound(id1);
        stopWaitingSound(id2);

        if (p1 == null || !p1.isOnline()) {
            if (p2 != null) joinQueue(p2);
            return;
        }
        if (p2 == null || !p2.isOnline()) {
            joinQueue(p1);
            return;
        }

        startMatch(p1, p2, arena);
    }

    private void startMatch(Player p1, Player p2, Arena arena) {
        arena.setInUse(true);
        plugin.getArenaManager().snapshot(arena);

        MatchSession session = new MatchSession(p1.getUniqueId(), p2.getUniqueId(), arena);
        activeMatches.put(p1.getUniqueId(), session);
        activeMatches.put(p2.getUniqueId(), session);

        plugin.teleportInternally(p1, arena.getSpawn1());
        plugin.teleportInternally(p2, arena.getSpawn2());

        p1.setInvulnerable(true);
        p2.setInvulnerable(true);

        startCountdown(session, p1, p2);
    }

    private void startCountdown(MatchSession session, Player p1, Player p2) {
        int seconds = plugin.getConfig().getInt("countdown.seconds", 5);
        Sound tick = safeSound(plugin.getConfig().getString("countdown.sound-tick", "block.note_block.hat"));
        Sound go = safeSound(plugin.getConfig().getString("countdown.sound-go", "entity.ender_dragon.growl"));

        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline()) {
                    cancel();
                    return;
                }
                if (remaining > 0) {
                    Title title = Title.title(
                            Component.text(String.valueOf(remaining), NamedTextColor.RED),
                            Component.text("Prepare-toi au combat !", NamedTextColor.YELLOW)
                    );
                    p1.showTitle(title);
                    p2.showTitle(title);
                    if (tick != null) {
                        p1.playSound(p1.getLocation(), tick, 1f, 1f);
                        p2.playSound(p2.getLocation(), tick, 1f, 1f);
                    }
                    remaining--;
                } else {
                    Title title = Title.title(
                            Component.text("COMBAT !", NamedTextColor.DARK_RED),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
                    );
                    p1.showTitle(title);
                    p2.showTitle(title);
                    if (go != null) {
                        p1.playSound(p1.getLocation(), go, 1f, 1f);
                        p2.playSound(p2.getLocation(), go, 1f, 1f);
                    }
                    p1.setInvulnerable(false);
                    p2.setInvulnerable(false);
                    session.pvpAllowed = true;
                    startShrink(session, p1, p2, session.arena);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ---------------------------------------------------------
    // Zone qui se retrecit si le combat dure trop longtemps
    // ---------------------------------------------------------

    private void startShrink(MatchSession session, Player p1, Player p2, Arena arena) {
        int afterTicks = plugin.getConfig().getInt("fight-timer.shrink-after-seconds", 300) * 20;
        int durationTicks = plugin.getConfig().getInt("fight-timer.shrink-duration-seconds", 60) * 20;
        double minRadius = plugin.getConfig().getDouble("fight-timer.min-radius", 3);
        double damage = plugin.getConfig().getDouble("fight-timer.damage-per-tick", 2.0);
        int intervalTicks = Math.max(1, plugin.getConfig().getInt("fight-timer.tick-interval-seconds", 1)) * 20;

        double centerX = arena.centerX();
        double centerZ = arena.centerZ();
        double maxRadius = arena.maxRadius();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Le combat est peut-etre deja fini a ce moment-la : dans ce cas on ne fait rien.
            if (getMatch(p1.getUniqueId()) != session) return;

            Component warn = Component.text("La zone de combat commence a se retrecir !", NamedTextColor.RED);
            if (p1.isOnline()) p1.sendMessage(warn);
            if (p2.isOnline()) p2.sendMessage(warn);

            long[] elapsed = {0L};
            BukkitTask[] holder = new BukkitTask[1];
            holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (getMatch(p1.getUniqueId()) != session) {
                    holder[0].cancel();
                    return;
                }
                elapsed[0] += intervalTicks;
                double progress = Math.min(1.0, elapsed[0] / (double) durationTicks);
                double currentRadius = maxRadius - (maxRadius - minRadius) * progress;

                pushBackIfOutside(p1, centerX, centerZ, currentRadius, damage);
                pushBackIfOutside(p2, centerX, centerZ, currentRadius, damage);
            }, 0L, intervalTicks);

            session.shrinkTask = holder[0];
        }, afterTicks);
    }

    private void pushBackIfOutside(Player p, double centerX, double centerZ, double radius, double damage) {
        if (p == null || !p.isOnline()) return;
        Location loc = p.getLocation();
        double dist = Math.hypot(loc.getX() - centerX, loc.getZ() - centerZ);
        if (dist > radius) {
            p.damage(damage);
            p.sendActionBar(Component.text("Hors zone ! Reviens vers le centre du combat !", NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------
    // Fin de combat + phase de loot pour le gagnant
    // ---------------------------------------------------------

    /**
     * Appelee a la mort d'un joueur : le perdant est renvoye immediatement (son stuff tombe au sol),
     * le gagnant reste sur place avec un delai pour looter avant d'etre renvoye automatiquement.
     */
    public void concludeMatchOnDeath(MatchSession session, UUID winnerUuid, UUID loserUuid,
                                      Location deathLocation, ItemStack[] droppedItems) {
        endSessionTracking(session);

        if (deathLocation != null && droppedItems != null) {
            for (ItemStack item : droppedItems) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    deathLocation.getWorld().dropItemNaturally(deathLocation, item);
                }
            }
        }

        Player loser = Bukkit.getPlayer(loserUuid);
        if (loser != null) {
            var maxHealthAttr = loser.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) loser.setHealth(maxHealthAttr.getValue());
            loser.setInvulnerable(false);
            loser.sendMessage(Component.text("Tu as perdu le combat. Ton stuff est reste sur place !", NamedTextColor.RED));
            plugin.sendPlayerHome(loser);
        }

        startLootPhase(winnerUuid, session.arena);
    }

    /** Appelee sur deconnexion en plein combat : forfait immediat (pas de phase de loot pour le fuyard). */
    public void concludeMatchOnQuit(MatchSession session, UUID winnerUuid, UUID quitterUuid) {
        endSessionTracking(session);
        // Le stuff du fuyard reste gere par le comportement de deconnexion standard du serveur.
        startLootPhase(winnerUuid, session.arena);
    }

    private void endSessionTracking(MatchSession session) {
        activeMatches.remove(session.player1);
        activeMatches.remove(session.player2);
        if (session.shrinkTask != null) session.shrinkTask.cancel();
    }

    private void startLootPhase(UUID winnerUuid, Arena arena) {
        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner == null || !winner.isOnline()) {
            // Personne pour looter : on regenere direct.
            finalizeArena(arena);
            return;
        }

        int graceSeconds = plugin.getConfig().getInt("loot.grace-period-seconds", 300);
        winner.setInvulnerable(true);
        winner.sendMessage(Component.text("Tu as gagne le combat !", NamedTextColor.GREEN));
        winner.sendMessage(Component.text(
                "Tu as " + (graceSeconds / 60) + " minutes pour looter le stuff avant d'etre renvoye automatiquement (ou fais /spawn quand tu veux).",
                NamedTextColor.GRAY));

        lootingPlayers.put(winnerUuid, arena);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> finishLooting(winnerUuid), graceSeconds * 20L);
        lootGraceTasks.put(winnerUuid, task);
    }

    /** A appeler quand le gagnant quitte lui-meme la zone de loot (ex: /spawn) avant la fin du delai. */
    public void notifyLeftLootZone(UUID uuid) {
        if (lootingPlayers.containsKey(uuid)) {
            finishLooting(uuid);
        }
    }

    /** A appeler si le gagnant se deconnecte pendant la phase de loot. */
    public void notifyQuitDuringLoot(UUID uuid) {
        if (lootingPlayers.containsKey(uuid)) {
            finishLooting(uuid);
        }
    }

    private void finishLooting(UUID uuid) {
        Arena arena = lootingPlayers.remove(uuid);
        if (arena == null) return;

        BukkitTask task = lootGraceTasks.remove(uuid);
        if (task != null) task.cancel();

        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            p.setInvulnerable(false);
            // S'il est encore dans l'arene, on le renvoie ; s'il est deja parti tout seul, on ne le re-teleporte pas.
            if (arena.contains(p.getLocation())) {
                plugin.sendPlayerHome(p);
            }
        }

        finalizeArena(arena);
    }

    private void finalizeArena(Arena arena) {
        plugin.getArenaManager().restore(arena, () -> {
            arena.setInUse(false);
            tryStartMatch();
        });
    }

    private Sound safeSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_').replace(':', '_'));
        } catch (Exception ignored) {
            return null;
        }
    }
}

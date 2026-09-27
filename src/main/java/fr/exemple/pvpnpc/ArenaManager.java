package fr.exemple.pvpnpc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class ArenaManager {

    private final PvpNpcPlugin plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    // Selections temporaires des admins pendant la creation d'une arene (/arenapvp pos1, pos2, ...)
    private final Map<UUID, Location> pos1Selection = new HashMap<>();
    private final Map<UUID, Location> pos2Selection = new HashMap<>();
    private final Map<UUID, Location> spawn1Selection = new HashMap<>();
    private final Map<UUID, Location> spawn2Selection = new HashMap<>();

    // Snapshot des blocs par arene pendant qu'un combat est en cours
    private final Map<String, Map<Long, BlockData>> snapshots = new HashMap<>();

    public ArenaManager(PvpNpcPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void setPos1(Player p) {
        pos1Selection.put(p.getUniqueId(), p.getLocation());
    }

    public void setPos2(Player p) {
        pos2Selection.put(p.getUniqueId(), p.getLocation());
    }

    public void setSpawn1(Player p) {
        spawn1Selection.put(p.getUniqueId(), p.getLocation());
    }

    public void setSpawn2(Player p) {
        spawn2Selection.put(p.getUniqueId(), p.getLocation());
    }

    /**
     * Cree une arene a partir des 4 points selectionnes par le joueur (pos1, pos2, spawn1, spawn2).
     * Renvoie un message d'erreur ou null si tout va bien.
     */
    public String createArena(Player p, String name) {
        UUID id = p.getUniqueId();
        if (arenas.containsKey(name)) return "Une arene s'appelle deja \"" + name + "\".";
        Location c1 = pos1Selection.get(id);
        Location c2 = pos2Selection.get(id);
        Location s1 = spawn1Selection.get(id);
        Location s2 = spawn2Selection.get(id);
        if (c1 == null || c2 == null) return "Definis d'abord les deux coins avec /arenapvp pos1 et /arenapvp pos2.";
        if (s1 == null || s2 == null) return "Definis d'abord les deux points de spawn avec /arenapvp spawn1 et /arenapvp spawn2.";
        if (!c1.getWorld().equals(c2.getWorld())) return "Les deux coins doivent etre dans le meme monde.";

        Arena arena = new Arena(name, c1.getWorld(), c1, c2, s1, s2);
        arenas.put(name, arena);
        saveToConfig();

        pos1Selection.remove(id);
        pos2Selection.remove(id);
        spawn1Selection.remove(id);
        spawn2Selection.remove(id);
        return null;
    }

    public void removeArena(String name) {
        arenas.remove(name);
        saveToConfig();
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getFreeArena() {
        for (Arena a : arenas.values()) {
            if (!a.isInUse()) return a;
        }
        return null;
    }

    // ---------------------------------------------------------
    // Sauvegarde / restauration des blocs (la "regen de terrain")
    // ---------------------------------------------------------

    public void snapshot(Arena arena) {
        Map<Long, BlockData> data = new HashMap<>();
        World w = arena.getWorld();
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int y = arena.minY(); y <= arena.maxY(); y++) {
                for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                    long key = blockKey(x, y, z);
                    data.put(key, w.getBlockAt(x, y, z).getBlockData());
                }
            }
        }
        snapshots.put(arena.getName(), data);
    }

    /**
     * Restaure les blocs de l'arene progressivement (par paquets) pour ne pas faire chuter le TPS
     * si l'arene est grande.
     */
    public void restore(Arena arena, Runnable onFinished) {
        Map<Long, BlockData> data = snapshots.remove(arena.getName());
        if (data == null) {
            if (onFinished != null) onFinished.run();
            return;
        }
        World w = arena.getWorld();
        Iterator<Map.Entry<Long, BlockData>> it = data.entrySet().iterator();
        int perTick = plugin.getConfig().getInt("restore.blocks-per-tick", 400);

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int count = 0;
            while (it.hasNext() && count < perTick) {
                Map.Entry<Long, BlockData> entry = it.next();
                int[] xyz = unpack(entry.getKey());
                w.getBlockAt(xyz[0], xyz[1], xyz[2]).setBlockData(entry.getValue(), false);
                count++;
            }
            if (!it.hasNext()) {
                task.cancel();
                if (onFinished != null) onFinished.run();
            }
        }, 0L, 1L);
    }

    private long blockKey(int x, int y, int z) {
        // Encodage simple x/y/z -> long, suffisant pour les coordonnees d'un monde Minecraft classique
        return (((long) (x + 30_000_000) & 0x3FFFFFFL) << 38)
                | (((long) (y + 512) & 0x7FFL) << 27)
                | ((long) (z + 30_000_000) & 0x7FFFFFFL);
    }

    private int[] unpack(long key) {
        int x = (int) ((key >> 38) & 0x3FFFFFFL) - 30_000_000;
        int y = (int) ((key >> 27) & 0x7FFL) - 512;
        int z = (int) (key & 0x7FFFFFFL) - 30_000_000;
        return new int[]{x, y, z};
    }

    // ---------------------------------------------------------
    // Persistance dans config.yml
    // ---------------------------------------------------------

    private void loadFromConfig() {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("arenas");
        for (Map<?, ?> m : list) {
            try {
                String name = (String) m.get("name");
                World world = Bukkit.getWorld((String) m.get("world"));
                if (world == null) continue;
                Location c1 = new Location(world, toD(m.get("c1x")), toD(m.get("c1y")), toD(m.get("c1z")));
                Location c2 = new Location(world, toD(m.get("c2x")), toD(m.get("c2y")), toD(m.get("c2z")));
                Location s1 = new Location(world, toD(m.get("s1x")), toD(m.get("s1y")), toD(m.get("s1z")), toF(m.get("s1yaw")), toF(m.get("s1pitch")));
                Location s2 = new Location(world, toD(m.get("s2x")), toD(m.get("s2y")), toD(m.get("s2z")), toF(m.get("s2yaw")), toF(m.get("s2pitch")));
                arenas.put(name, new Arena(name, world, c1, c2, s1, s2));
            } catch (Exception ex) {
                plugin.getLogger().warning("Impossible de charger une arene depuis la config : " + ex.getMessage());
            }
        }
    }

    private double toD(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }
    private float toF(Object o) { return o == null ? 0 : ((Number) o).floatValue(); }

    public void saveToConfig() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Arena a : arenas.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("world", a.getWorld().getName());
            m.put("c1x", a.getCorner1().getX());
            m.put("c1y", a.getCorner1().getY());
            m.put("c1z", a.getCorner1().getZ());
            m.put("c2x", a.getCorner2().getX());
            m.put("c2y", a.getCorner2().getY());
            m.put("c2z", a.getCorner2().getZ());
            m.put("s1x", a.getSpawn1().getX());
            m.put("s1y", a.getSpawn1().getY());
            m.put("s1z", a.getSpawn1().getZ());
            m.put("s1yaw", a.getSpawn1().getYaw());
            m.put("s1pitch", a.getSpawn1().getPitch());
            m.put("s2x", a.getSpawn2().getX());
            m.put("s2y", a.getSpawn2().getY());
            m.put("s2z", a.getSpawn2().getZ());
            m.put("s2yaw", a.getSpawn2().getYaw());
            m.put("s2pitch", a.getSpawn2().getPitch());
            list.add(m);
        }
        plugin.getConfig().set("arenas", list);
        plugin.saveConfig();
    }
}

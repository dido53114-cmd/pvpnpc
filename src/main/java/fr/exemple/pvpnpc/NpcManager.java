package fr.exemple.pvpnpc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Cree et gere le PNJ (un Villageois fige, invulnerable, sans IA) sur lequel
 * les joueurs peuvent faire un clic droit pour etre envoyes vers un autre serveur du reseau.
 */
public class NpcManager {

    private final PvpNpcPlugin plugin;
    public static final String TAG_KEY = "pvpnpc_marker";
    public static final String TARGET_KEY = "pvpnpc_target_server";

    public NpcManager(PvpNpcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Supprime les PNJ existants puis les respawn depuis la config (a appeler dans onEnable). */
    public void loadAll() {
        NamespacedKey tagKey = new NamespacedKey(plugin, TAG_KEY);
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Entity e : new ArrayList<>(w.getEntities())) {
                if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }

        List<Map<?, ?>> list = plugin.getConfig().getMapList("npcs");
        for (Map<?, ?> m : list) {
            try {
                String world = (String) m.get("world");
                double x = ((Number) m.get("x")).doubleValue();
                double y = ((Number) m.get("y")).doubleValue();
                double z = ((Number) m.get("z")).doubleValue();
                float yaw = m.get("yaw") != null ? ((Number) m.get("yaw")).floatValue() : 0f;
                String target = (String) m.get("target");
                org.bukkit.World w = plugin.getServer().getWorld(world);
                if (w == null) continue;
                spawn(new Location(w, x, y, z, yaw, 0), target, false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Impossible de charger un PNJ depuis la config : " + ex.getMessage());
            }
        }
    }

    public void createAndSave(Location loc, String targetServer) {
        spawn(loc, targetServer, true);
    }

    private void spawn(Location loc, String targetServer, boolean persist) {
        Villager villager = loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(true);
            v.setCustomName(Component.text("Combat PvP", NamedTextColor.RED)
                    .appendNewline()
                    .append(Component.text("Clique pour rejoindre !", NamedTextColor.GRAY)));
            v.setCustomNameVisible(true);
            v.setProfession(Villager.Profession.NONE);
            v.getPersistentDataContainer().set(new NamespacedKey(plugin, TAG_KEY), PersistentDataType.BYTE, (byte) 1);
            v.getPersistentDataContainer().set(new NamespacedKey(plugin, TARGET_KEY), PersistentDataType.STRING, targetServer);
        });
        villager.setRemoveWhenFarAway(false);

        if (persist) {
            List<Map<String, Object>> list = new ArrayList<>(plugin.getConfig().getMapList("npcs"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", loc.getWorld().getName());
            m.put("x", loc.getX());
            m.put("y", loc.getY());
            m.put("z", loc.getZ());
            m.put("yaw", loc.getYaw());
            m.put("target", targetServer);
            list.add(m);
            plugin.getConfig().set("npcs", list);
            plugin.saveConfig();
        }
    }

    /** Supprime le PNJ le plus proche du joueur (rayon 5 blocs), y compris dans la config. */
    public boolean removeNearest(Player p) {
        NamespacedKey tagKey = new NamespacedKey(plugin, TAG_KEY);
        org.bukkit.entity.Entity found = null;
        for (org.bukkit.entity.Entity e : p.getNearbyEntities(5, 5, 5)) {
            if (e.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                found = e;
                break;
            }
        }
        if (found == null) return false;

        Location loc = found.getLocation();
        found.remove();

        List<Map<?, ?>> list = plugin.getConfig().getMapList("npcs");
        List<Map<String, Object>> newList = new ArrayList<>();
        for (Map<?, ?> m : list) {
            double x = ((Number) m.get("x")).doubleValue();
            double y = ((Number) m.get("y")).doubleValue();
            double z = ((Number) m.get("z")).doubleValue();
            if (Math.abs(x - loc.getX()) < 0.5 && Math.abs(y - loc.getY()) < 0.5 && Math.abs(z - loc.getZ()) < 0.5) {
                continue; // on saute celui qu'on vient de supprimer
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) m;
            newList.add(casted);
        }
        plugin.getConfig().set("npcs", newList);
        plugin.saveConfig();
        return true;
    }
}

package fr.exemple.pvpnpc;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Represente une "parcelle" d'arene : une zone cuboide dans laquelle
 * un combat va se derouler, avec deux points de spawn pour les joueurs.
 */
public class Arena {

    private final String name;
    private final World world;
    private final Location corner1;
    private final Location corner2;
    private final Location spawn1;
    private final Location spawn2;

    private boolean inUse = false;

    public Arena(String name, World world, Location corner1, Location corner2, Location spawn1, Location spawn2) {
        this.name = name;
        this.world = world;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        return world;
    }

    public Location getCorner1() {
        return corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public int minX() {
        return Math.min(corner1.getBlockX(), corner2.getBlockX());
    }

    public int minY() {
        return Math.min(corner1.getBlockY(), corner2.getBlockY());
    }

    public int minZ() {
        return Math.min(corner1.getBlockZ(), corner2.getBlockZ());
    }

    public int maxX() {
        return Math.max(corner1.getBlockX(), corner2.getBlockX());
    }

    public int maxY() {
        return Math.max(corner1.getBlockY(), corner2.getBlockY());
    }

    public int maxZ() {
        return Math.max(corner1.getBlockZ(), corner2.getBlockZ());
    }

    public double centerX() {
        return (minX() + maxX()) / 2.0 + 0.5;
    }

    public double centerZ() {
        return (minZ() + maxZ()) / 2.0 + 0.5;
    }

    /** Rayon (horizontal, XZ) du plus grand cercle inscrit reliant le centre a un coin de l'arene. */
    public double maxRadius() {
        double dx = (maxX() - minX()) / 2.0;
        double dz = (maxZ() - minZ()) / 2.0;
        return Math.hypot(dx, dz);
    }

    /**
     * Vrai si la location donnee (meme monde) est a l'interieur du cuboide de l'arene.
     * Utilise pour s'assurer qu'un coup porte hors-zone n'est pas compte comme du PvP d'arene.
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(world)) return false;
        return loc.getBlockX() >= minX() && loc.getBlockX() <= maxX()
                && loc.getBlockY() >= minY() && loc.getBlockY() <= maxY()
                && loc.getBlockZ() >= minZ() && loc.getBlockZ() <= maxZ();
    }
}

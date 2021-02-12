/*
 * Copyright (c) 2020. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.internals.hooks;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.wasteofplastic.askyblock.ASkyBlockAPI;
import com.wasteofplastic.askyblock.Island;
import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class HookChecker {

    private HyperDriveTuga pluginInstance;
    private boolean factionsInstalled, factionsUUID, townyInstalled, griefPreventionInstalled, aSkyBlockInstalled, residenceInstalled, prismaInstalled;
    private Plugin essentialsPlugin;

    public HookChecker(HyperDriveTuga pluginInstance) {
        setPluginInstance(pluginInstance);

        Plugin factionsPlugin = getPluginInstance().getServer().getPluginManager().getPlugin("Factions");
        setFactionsInstalled(factionsPlugin != null);
        setFactionsUUID(factionsPlugin != null && factionsPlugin.getDescription().getDepend().contains("MassiveCore"));

        setASkyBlockInstalled(getPluginInstance().getServer().getPluginManager().getPlugin("ASkyBlock") != null);
        setGriefPreventionInstalled(getPluginInstance().getServer().getPluginManager().getPlugin("GriefPrevention") != null);
        setTownyInstalled(getPluginInstance().getServer().getPluginManager().getPlugin("Towny") != null);
        setResidenceInstalled(getPluginInstance().getServer().getPluginManager().getPlugin("Residence") != null);
        setPrismaInstalled(getPluginInstance().getServer().getPluginManager().getPlugin("Prisma") != null);

        setEssentialsPlugin(getPluginInstance().getServer().getPluginManager().getPlugin("Essentials"));
        if (getEssentialsPlugin() == null)
            setEssentialsPlugin(getPluginInstance().getServer().getPluginManager().getPlugin("EssentialsEx"));
    }

    /**
     * Checks to see if the location is safe and doesn't collide with supported plugin's hook systems.
     *
     * @param player   The player to check.
     * @param location The location to check safety for.
     * @return Whether it is safe.
     */
    public boolean isLocationHookSafe(Player player, Location location) {
        if (player.hasPermission("hyperdrive.admin.bypass")) return true;

        boolean isSafeLocation = true;
        if (getPluginInstance().getWorldGuardHandler() != null && !getPluginInstance().getWorldGuardHandler().passedWorldGuardHook(location))
            isSafeLocation = false;

        if (isFactionsInstalled()) {
                FLocation fLocation = new FLocation(location);
                com.massivecraft.factions.Faction factionAtLocation = Board.getInstance().getFactionAt(fLocation);
                FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
                if (!factionAtLocation.isWilderness() && !fPlayer.getFaction().getComparisonTag().equalsIgnoreCase(factionAtLocation.getComparisonTag()))
                    isSafeLocation = false;
        }

        if (isASkyBlockInstalled()) {
            Island island = ASkyBlockAPI.getInstance().getIslandAt(location);
            if (island != null && !island.getOwner().toString().equals(player.getUniqueId().toString()) && !island.getMembers().contains(player.getUniqueId()))
                isSafeLocation = false;
        }

        if (isGriefPreventionInstalled()) {
            Claim claimAtLocation = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claimAtLocation != null)
                isSafeLocation = false;
        }

        if (isTownyInstalled()) {
            try {
                Town town = WorldCoord.parseWorldCoord(location).getTownBlock().getTown();
                if (town != null) isSafeLocation = false;
            } catch (Exception ignored) {}
        }

        if (isResidenceInstalled()) {
            ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(location);
            if (res != null) isSafeLocation = false;
        }

        return isSafeLocation;
    }

    // getters & setters
    public boolean isFactionsInstalled() {
        return factionsInstalled;
    }

    private void setFactionsInstalled(boolean factionsInstalled) {
        this.factionsInstalled = factionsInstalled;
    }

    public boolean isTownyInstalled() {
        return townyInstalled;
    }

    private void setTownyInstalled(boolean townyInstalled) {
        this.townyInstalled = townyInstalled;
    }

    public boolean isGriefPreventionInstalled() {
        return griefPreventionInstalled;
    }

    private void setGriefPreventionInstalled(boolean griefPreventionInstalled) {
        this.griefPreventionInstalled = griefPreventionInstalled;
    }

    public boolean isASkyBlockInstalled() {
        return aSkyBlockInstalled;
    }

    private void setASkyBlockInstalled(boolean aSkyBlockInstalled) {
        this.aSkyBlockInstalled = aSkyBlockInstalled;
    }

    public boolean isResidenceInstalled() {
        return residenceInstalled;
    }

    private void setResidenceInstalled(boolean residenceInstalled) {
        this.residenceInstalled = residenceInstalled;
    }

    public HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    private void setFactionsUUID(boolean factionsUUID) {
        this.factionsUUID = factionsUUID;
    }

    private void setPrismaInstalled(boolean prismaInstalled) {
        this.prismaInstalled = prismaInstalled;
    }

    public Plugin getEssentialsPlugin() {
        return essentialsPlugin;
    }

    private void setEssentialsPlugin(Plugin essentialsPlugin) {
        this.essentialsPlugin = essentialsPlugin;
    }
}

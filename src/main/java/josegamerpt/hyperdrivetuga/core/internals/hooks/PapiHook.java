/*
 * Copyright (c) 2020. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.internals.hooks;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PapiHook extends PlaceholderExpansion {

    private HyperDriveTuga pluginInstance;

    public PapiHook(HyperDriveTuga pluginInstance) {
        setPluginInstance(pluginInstance);

    }

    public String replace(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public String replace(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return getPluginInstance().getDescription().getAuthors().toString();
    }

    @Override
    public String getIdentifier() {
        return "HyperDrive";
    }

    @Override
    public String getVersion() {
        return getPluginInstance().getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        switch (identifier.toLowerCase()) {
            case "limit":
                return String.valueOf(getPluginInstance().getManager().getWarpLimit(player));
            case "count":
                return String.valueOf(getPluginInstance().getManager().getPermittedWarps(player).size());
            default:
                return null;
        }
    }

    // getters & setters
    private HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }
}

/*
 * Copyright (c) 2019. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.packets.titles.versions;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import org.bukkit.entity.Player;
import josegamerpt.hyperdrivetuga.core.packets.titles.TitleHandler;

public class Titles_Latest implements TitleHandler {
    private HyperDriveTuga pluginInstance;

    public Titles_Latest(HyperDriveTuga pluginInstance) {
        setPluginInstance(pluginInstance);
    }

    @Override
    public void sendTitle(Player player, String text, int fadeIn, int displayTime, int fadeOut) {
        player.sendTitle(text, "", fadeIn * 20, displayTime * 20, fadeOut * 20);
    }

    @Override
    public void sendSubTitle(Player player, String text, int fadeIn, int displayTime, int fadeOut) {
        player.sendTitle("", getPluginInstance().getManager().colorText(text), fadeIn * 20, displayTime * 20, fadeOut * 20);
    }

    @Override
    public void sendTitle(Player player, String title, String subTitle, int fadeIn, int displayTime, int fadeOut) {
        player.sendTitle(getPluginInstance().getManager().colorText(title), getPluginInstance().getManager().colorText(subTitle),
                fadeIn * 20, displayTime * 20, fadeOut * 20);
    }

    private HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }
}

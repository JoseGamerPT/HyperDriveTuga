/*
 * Copyright (c) 2020. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.packets.actionbars.versions;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import josegamerpt.hyperdrivetuga.core.packets.actionbars.ActionBarHandler;

public class ABH_Latest implements ActionBarHandler {
    @Override
    public void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(HyperDriveTuga.getPluginInstance().getManager().colorText(message)));
    }

}

/*
 * Copyright (c) 2020. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.internals;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import josegamerpt.hyperdrivetuga.api.EnumContainer;
import josegamerpt.hyperdrivetuga.core.objects.GroupTemp;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Animation {

    private HyperDriveTuga pluginInstance;
    private HashMap<UUID, BukkitTask> activeAnimationMap;

    public Animation(HyperDriveTuga pluginInstance) {
        setPluginInstance(pluginInstance);
        setActiveAnimationMap(new HashMap<>());
    }

    public void playAnimation(Player player, String particleEffect, EnumContainer.Animation animationType, int duration) {
        if (player == null || (particleEffect == null || particleEffect.isEmpty()) || duration <= 0) return;

        switch (animationType) {
            case CONE:

                updateMap(player, new BukkitRunnable() {
                    int time;
                    double t;

                    @Override
                    public void run() {
                        if (time >= 20 * duration) cancel();

                        t += 0.5;
                        time += 1;

                        Location location = player.getLocation().clone();

                        double piDivided = Math.PI / 8;
                        for (double theta = -piDivided; (theta += piDivided) <= (Math.PI * 2); ) {
                            for (double i = -8; (i += 8) <= 1; ) {
                                double radius = 0.55, x = (radius * ((Math.PI * 2) - theta) * 0.5 * Math.cos(theta + t + i * Math.PI)),
                                        y = (radius * theta), z = (radius * ((Math.PI * 2) - theta) * 0.5 * Math.sin(theta + t + i * Math.PI));

                                location.add(x, y, z);
                                getPluginInstance().getManager().displayParticle(location, particleEffect);
                                location.subtract(x, y, z);
                            }
                        }
                    }
                }.runTaskTimerAsynchronously(getPluginInstance(), 0, 1));

                break;

            case HELIX:

                updateMap(player, new BukkitRunnable() {
                    int time;
                    double t;

                    @Override
                    public void run() {
                        if (time >= 20 * duration) cancel();

                        t += Math.PI / 8;
                        if (t >= 5 * (8 * (Math.PI / 8))) t = 0;
                        time += 1;

                        Location location = player.getLocation().clone();
                        double x = 1 * Math.cos(t), y = t, z = 1 * Math.sin(t);

                        location.add(x, y, z);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x, y, z);
                    }
                }.runTaskTimerAsynchronously(getPluginInstance(), 0, 1));

                break;

            case RING:

                updateMap(player, new BukkitRunnable() {
                    int time;
                    double t, nt;

                    @Override
                    public void run() {
                        if (time >= 20 * duration) cancel();

                        t += 0;
                        nt += 0;
                        time += 1;

                        Location location = player.getLocation().clone();

                        t += Math.PI / 8;
                        double x = -0.75 * Math.cos(t), z = -0.75 * Math.sin(t);

                        location.add(x, 0.5, z);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x, 0.5, z);

                        location.add(x, 1, z);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x, 1, z);

                        location.add(x, 1.5, z);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x, 1.5, z);

                        nt += Math.PI / 8;

                        double x2 = 1 * Math.cos(nt), z2 = 1 * Math.sin(nt);

                        location.add(x2, 0.5, z2);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x2, 0.5, z2);

                        location.add(x2, 1, z2);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x2, 1, z2);

                        location.add(x2, 1.5, z2);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x2, 1.5, z2);
                    }
                }.runTaskTimerAsynchronously(getPluginInstance(), 0, 1));

                break;

            case CIRCLE:

                updateMap(player, new BukkitRunnable() {
                    double t;
                    int time;

                    public void run() {
                        if (time >= 20 * duration) cancel();

                        t += Math.PI / 8;
                        time += 1;

                        Location location = player.getLocation().clone();

                        double piDivided = Math.PI / 8;
                        for (double theta = -piDivided; (theta += piDivided) <= (Math.PI * 2); ) {
                            double x = (1.75 * Math.cos(theta) * Math.sin(t)), y = (1.75 * Math.cos(t) + 1.5),
                                    z = (1.75 * Math.sin(theta) * Math.sin(t));

                            location.add(x, y, z);
                            getPluginInstance().getManager().displayParticle(location, particleEffect);
                            location.subtract(x, y, z);
                        }
                    }
                }.runTaskTimerAsynchronously(getPluginInstance(), 0, 1));

                break;

            case VORTEX:

                updateMap(player, new BukkitRunnable() {
                    int time;
                    double t, nt;

                    @Override
                    public void run() {
                        if (time >= 20 * duration) cancel();

                        t += 0;
                        nt += 0;
                        time += 1;

                        Location location = player.getLocation().clone(), location2 = player.getLocation().clone();
                        t += Math.PI / 8;
                        double x = -1 * Math.cos(t), y = 1 * Math.sin(t), z = -1 * Math.sin(t);
                        location.add(x, y, z);
                        getPluginInstance().getManager().displayParticle(location, particleEffect);
                        location.subtract(x, y, z);
                        nt += Math.PI / 8;

                        double x2 = 1 * Math.cos(nt), y2 = 1 * Math.sin(nt), z2 = 1 * Math.sin(nt);
                        location2.add(x2, y2, z2);
                        getPluginInstance().getManager().displayParticle(location2, particleEffect);
                        location2.subtract(x2, y2, z2);
                    }
                }.runTaskTimer(getPluginInstance(), 0, 1));

                break;

            default:
                break;
        }
    }

    public void stopActiveAnimation(Player player) {
        if (!getActiveAnimationMap().isEmpty() && getActiveAnimationMap().containsKey(player.getUniqueId())) {
            BukkitTask bukkitTask = getActiveAnimationMap().get(player.getUniqueId());
            if (bukkitTask != null) bukkitTask.cancel();
            getActiveAnimationMap().remove(player.getUniqueId());
        }
    }

    public void stopGroupActiveAnimation(GroupTemp groupTemp) {
        List<UUID> acceptedPlayers = groupTemp.getAcceptedPlayers();
        for (int i = -1; ++i < acceptedPlayers.size(); ) {
            UUID playerUniqueId = acceptedPlayers.get(i);
            if (playerUniqueId != null)
                if (!getActiveAnimationMap().isEmpty() && getActiveAnimationMap().containsKey(playerUniqueId)) {
                    BukkitTask bukkitTask = getActiveAnimationMap().get(playerUniqueId);
                    if (bukkitTask != null) bukkitTask.cancel();
                    getActiveAnimationMap().remove(playerUniqueId);
                }
        }
    }

    private void updateMap(Player player, BukkitTask bukkitTask) {
        stopActiveAnimation(player);
        getActiveAnimationMap().put(player.getUniqueId(), bukkitTask);
    }

    private HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    private HashMap<UUID, BukkitTask> getActiveAnimationMap() {
        return activeAnimationMap;
    }

    private void setActiveAnimationMap(HashMap<UUID, BukkitTask> activeAnimationMap) {
        this.activeAnimationMap = activeAnimationMap;
    }
}

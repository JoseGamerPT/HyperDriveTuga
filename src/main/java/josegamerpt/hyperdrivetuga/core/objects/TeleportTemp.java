/*
 * Copyright (c) 2019. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.objects;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;

public class TeleportTemp {
    private HyperDriveTuga pluginInstance;
    private String teleportTypeId, teleportValue;
    private int seconds;

    public TeleportTemp(HyperDriveTuga pluginInstance, String teleportTypeId, String teleportValue, int seconds) {
        setPluginInstance(pluginInstance);
        setTeleportTypeId(teleportTypeId);
        setTeleportValue(teleportValue);
        setSeconds(seconds);
    }

    // getters & setters
    public HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public String getTeleportTypeId() {
        return teleportTypeId;
    }

    private void setTeleportTypeId(String teleportTypeId) {
        this.teleportTypeId = teleportTypeId;
    }

    public String getTeleportValue() {
        return teleportValue;
    }

    private void setTeleportValue(String teleportValue) {
        this.teleportValue = teleportValue;
    }
}

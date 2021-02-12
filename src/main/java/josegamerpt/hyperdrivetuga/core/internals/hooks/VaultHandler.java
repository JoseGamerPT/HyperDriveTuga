/*
 * Copyright (c) 2020. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.internals.hooks;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import net.milkbowl.vault.economy.Economy;

import java.util.Objects;

public class VaultHandler {

    private HyperDriveTuga pluginInstance;
    private Economy economy;

    public VaultHandler(HyperDriveTuga pluginInstance) {
        setPluginInstance(pluginInstance);
        setEconomy(Objects.requireNonNull(getPluginInstance().getServer().getServicesManager().getRegistration(Economy.class)).getProvider());
    }

    public Economy getEconomy() {
        return economy;
    }

    private void setEconomy(Economy economy) {
        this.economy = economy;
    }

    private HyperDriveTuga getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(HyperDriveTuga pluginInstance) {
        this.pluginInstance = pluginInstance;
    }
}

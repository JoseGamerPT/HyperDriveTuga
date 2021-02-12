/*
 * Copyright (c) 2019. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.objects;

import josegamerpt.hyperdrivetuga.HyperDriveTuga;
import josegamerpt.hyperdrivetuga.api.objects.SerializableLocation;
import josegamerpt.hyperdrivetuga.api.objects.Warp;
import org.bukkit.Location;

public class Destination {

    private SerializableLocation location;
    private Warp warp;

    public Destination(HyperDriveTuga pluginInstance, Location location) {
        setLocation(location);
        setWarp(null);
    }

    public Destination(SerializableLocation location) {
        setLocation(location);
        setWarp(null);
    }

    public SerializableLocation getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = new SerializableLocation(location);
    }

    public void setLocation(SerializableLocation location) {
        this.location = location;
    }

    public Warp getWarp() {
        return warp;
    }

    public void setWarp(Warp warp) {
        this.warp = warp;
    }
}

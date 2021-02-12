/*
 * Copyright (c) 2019. All rights reserved.
 */

package josegamerpt.hyperdrivetuga.core.packets.particles.versions;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import josegamerpt.hyperdrivetuga.core.packets.particles.ParticleHandler;

public class Particle_Latest implements ParticleHandler {
    @Override
    public void displayParticle(String particleName, Location location, int offsetX, int offsetY, int offsetZ,
                                int red, int green, int blue, int brightness, int speed, int amount) {
        Particle particle = Particle.valueOf(particleName);
        if (particle == Particle.REDSTONE)
            location.getWorld().spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, new Particle.DustOptions(Color.fromBGR(red, green, blue), 1));
        else location.getWorld().spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, 0);
    }

    @Override
    public void displayParticle(String particleName, Location location, int offsetX, int offsetY, int offsetZ, int speed, int amount) {
        Particle particle = Particle.valueOf(particleName);
        if (particle == Particle.REDSTONE)
            location.getWorld().spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, new Particle.DustOptions(Color.RED, 1));
        else location.getWorld().spawnParticle(particle, location, amount, offsetX, offsetY, offsetZ, 0);
    }
}

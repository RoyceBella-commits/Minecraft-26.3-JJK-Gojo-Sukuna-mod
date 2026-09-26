package cn.blockforge.ryomensukuna.m2a542fea.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CurseFx {
    private CurseFx() {
    }

    public static void particles(Level world, ParticleOptions effect, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (world.isClientSide()) {
            return;
        }
        ((ServerLevel)world).sendParticles(effect, x, y, z, count, dx, dy, dz, speed);
    }

    public static void curseAura(Level world, Vec3 center, int count, double radius, ParticleOptions effect) {
        CurseFx.particles(world, effect, center.x, center.y, center.z, count, radius, 0.4, radius, 0.012);
    }

    /** Particles for everyone nearby except one player (a caster's own view stays clear). */
    public static void particlesExcept(ServerLevel level, ServerPlayer except, ParticleOptions effect, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer == except || viewer.distanceToSqr(x, y, z) > 16384.0) continue;
            level.sendParticles(viewer, effect, false, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public static void particlesTo(ServerPlayer player, ParticleOptions effect, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        ((ServerLevel)player.level()).sendParticles(player, effect, true, false, x, y, z, count, dx, dy, dz, speed);
    }
}


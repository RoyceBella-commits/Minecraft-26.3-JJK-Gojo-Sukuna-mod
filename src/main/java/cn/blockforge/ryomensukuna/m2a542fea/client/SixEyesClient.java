package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.entity.MahoragaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * Six Eyes (passive): enemies in plain sight get a glowing outline, so they stand out even in
 * darkness. Only unobstructed enemies are marked; nothing is revealed through walls.
 */
public final class SixEyesClient {
    private static final double RANGE = 32.0;
    private static final Set<Integer> MARKED = new HashSet<>();
    private static int ticks;

    private SixEyesClient() {
    }

    public static boolean marked(Entity e) {
        return !MARKED.isEmpty() && MARKED.contains(e.getId());
    }

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MARKED.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ++ticks;
            if (client.player == null || client.level == null || SukunaClientState.route != StageRules.GOJO || !SukunaClientState.awakened()) {
                MARKED.clear();
                return;
            }
            if (client.isPaused() || ticks % 5 != 0) return;
            MARKED.clear();
            boolean marker = ticks % 10 == 0;
            DustParticleOptions mark = new DustParticleOptions(0x8FE0FF, 0.7f);
            for (LivingEntity e : client.level.getEntitiesOfClass(LivingEntity.class, new AABB(client.player.position(), client.player.position()).inflate(RANGE),
                    e -> (e instanceof Enemy || e instanceof MahoragaEntity) && e.isAlive() && !e.isInvisible() && e != client.player)) {
                if (!client.player.hasLineOfSight(e)) continue;
                MARKED.add(e.getId());
                if (marker) client.level.addParticle(mark, e.getX(), e.getY() + e.getBbHeight() + 0.45, e.getZ(), 0.0, 0.0, 0.0);
            }
        });
    }
}

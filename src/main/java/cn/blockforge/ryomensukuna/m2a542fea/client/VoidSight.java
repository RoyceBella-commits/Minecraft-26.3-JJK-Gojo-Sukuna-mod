package cn.blockforge.ryomensukuna.m2a542fea.client;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Whether the camera is inside an open Unlimited Void this frame. Then the terrain is not drawn at
 * all: the viewer floats in the Void's cosmos, with only the creatures and techniques around them
 * left visible. In a clash the shrine's composite draws its water on its own half and the Void's
 * cosmos below the Void's half, so no ground is needed on either side.
 */
public final class VoidSight {
    private static boolean hide;
    /** When the ground last gave way to the cosmos (nanoTime), for the white veil; 0 = never. */
    private static long enteredAt;
    /** The white veil that sweeps the ground away fades out over this long. */
    private static final float VEIL_SECONDS = 0.7f;

    private VoidSight() {
    }

    public static boolean hidesTerrain() {
        return hide;
    }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            boolean now = inside(ctx.level(), ctx.camera().position(), ctx.deltaTracker().getGameTimeDeltaPartialTick(false));
            if (now && !hide) enteredAt = System.nanoTime();
            hide = now;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            hide = false;
            enteredAt = 0L;
        });
        // A brief white veil as the ground gives way, so the world does not simply blink out.
        HudElementRegistry.addFirst(SukunaMod.id("void_veil"), (ctx, tracker) -> {
            float alpha = veil();
            if (alpha > 0.004f) ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), (int)(alpha * 255.0f) << 24 | 0xF4F7FF);
        });
    }

    /** Opacity of the white veil right after entering a Void. */
    private static float veil() {
        if (enteredAt == 0L) return 0.0f;
        float t = (System.nanoTime() - enteredAt) / 1.0E9f / VEIL_SECONDS;
        if (t >= 1.0f) return 0.0f;
        return 0.85f * (1.0f - t) * (1.0f - t);
    }

    private static boolean inside(ClientLevel level, Vec3 camera, float partial) {
        double full = VoidDomainEntity.RADIUS;
        for (VoidDomainEntity v : level.getEntities(SukunaMod.VOID_DOMAIN, new AABB(camera, camera).inflate(full + 2.0), e -> !e.isRemoved() && e.closeAt() < 0)) {
            double r = full * VoidDomainEntity.openFraction(v.age() + partial);
            Vec3 c = v.getPosition(partial).add(0.0, 0.5, 0.0);
            if (camera.distanceToSqr(c) < r * r) return true;
        }
        return false;
    }
}

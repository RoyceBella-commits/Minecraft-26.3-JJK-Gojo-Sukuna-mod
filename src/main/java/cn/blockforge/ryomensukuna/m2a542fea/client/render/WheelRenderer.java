package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Mahoraga's wheel over Sukuna's head while his Mahoraga stands: the very same wheel model, smaller,
 * turning an eighth at each completed adaptation stage just as Mahoraga's does.
 */
public final class WheelRenderer {
    private static final Identifier TEXTURE = SukunaMod.id("textures/entity/mahoraga_refined.png");
    /** Model scale (Mahoraga's own is 0.85); about three quarters of a block across. */
    private static final float SCALE = 0.62f;
    /** Height of the wheel's hub above the top of the bearer's head. */
    private static final double LIFT = 0.45;
    /** A wheel not refreshed by the server for this long is dropped (its bearer left our view). */
    private static final long STALE_TICKS = 60L;
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<Integer, State> WHEELS = new HashMap<>();
    private static ModelPart wheel;
    private static List<PortBuffers.Batch> extracted = List.of();

    private record State(int steps, long turnedAt, long seen) {
    }

    private WheelRenderer() {
    }

    public static void receive(int entityId, boolean shown, int steps, long turnedAt) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (shown) {
            WHEELS.put(entityId, new State(steps, turnedAt, level.getGameTime()));
        } else {
            WHEELS.remove(entityId);
        }
    }

    private static ModelPart wheel() {
        if (wheel == null) {
            wheel = MahoragaGeometry.create().getChild("body").getChild("wheel");
        }
        return wheel;
    }

    /** Same easing as Mahoraga's wheel: an eighth of a turn per stage, eased over 12 ticks. */
    private static float rotation(State s, long now, float delta) {
        float t = Mth.clamp(((float)(now - s.turnedAt) + delta) / 12.0f, 0.0f, 1.0f);
        t = t * t * (3.0f - 2.0f * t);
        return ((float)s.steps - (s.steps > 0 ? 1.0f - t : 0.0f)) * (float)Math.PI / 4.0f;
    }

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            WHEELS.clear();
            extracted = List.of();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> PortBuffers.submit(extracted, ctx.poseStack(), ctx.submitNodeCollector()));
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> {
            if (WHEELS.isEmpty()) {
                extracted = List.of();
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            long now = ctx.level().getGameTime();
            float delta = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 camera = ctx.camera().position();
            PortBuffers buffers = new PortBuffers();
            ModelPart part = wheel();
            for (Iterator<Map.Entry<Integer, State>> it = WHEELS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, State> e = it.next();
                State s = e.getValue();
                Entity bearer = ctx.level().getEntity(e.getKey());
                if (bearer == null || !bearer.isAlive() || now - s.seen > STALE_TICKS) {
                    if (now - s.seen > STALE_TICKS) it.remove();
                    continue;
                }
                // In first person the wheel would only clutter the sky above the player's own view.
                if (bearer == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                double x = Mth.lerp(delta, bearer.xo, bearer.getX());
                double y = Mth.lerp(delta, bearer.yo, bearer.getY()) + bearer.getBbHeight() + LIFT;
                double z = Mth.lerp(delta, bearer.zo, bearer.getZ());
                PoseStack pose = new PoseStack();
                pose.pushPose();
                pose.translate(x - camera.x, y - camera.y, z - camera.z);
                pose.rotate(new Quaternionf().rotationY((float)Math.toRadians(180.0f - bearer.getYRot())));
                pose.scale(-SCALE, -SCALE, SCALE);
                part.resetPose();
                part.x = 0.0f;
                part.y = 0.0f;
                part.z = 0.0f;
                part.yRot = rotation(s, now, delta);
                part.render(pose, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
                pose.popPose();
            }
            extracted = buffers.snapshot();
        });
    }
}

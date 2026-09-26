package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.entity.AkaEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.AoEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.MurasakiEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.TechniqueEntity;
import cn.blockforge.ryomensukuna.m2a542fea.entity.VoidDomainEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Renderers for Gojo technique bodies, reusing the original mod's rift shader pipeline. */
public final class GojoRenderers {
    private GojoRenderers() {
    }

    private static Vec3[] billboard() {
        Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 look = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1.0, 0.0, 0.0);
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        return new Vec3[]{right, up};
    }

    static float phase(TechniqueEntity e, float delta) {
        return ((e.age() + delta) % 40.0f) / 40.0f;
    }

    public static class Orb<T extends TechniqueEntity> extends PortEntityRenderer<T> {
        private final float kind;
        private final float scale;
        private final float centerY;

        public Orb(EntityRendererProvider.Context ctx, float kind, float scale, float centerY) {
            super(ctx);
            this.kind = kind;
            this.scale = scale;
            this.centerY = centerY;
            this.shadowRadius = 0.0f;
        }

        @Override
        protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(T e, float partialTicks) {
            return e.getBoundingBox().inflate(Math.max(1.0, e.size()));
        }

        @Override
        protected void render(T e, float yaw, float delta, PoseStack pose, PortBuffers buffers, int light) {
            Vec3[] axes = billboard();
            float pulse = 1.0f + 0.06f * (float)Math.sin((e.age() + delta) * 0.6f);
            float radius = Math.max(0.3f, e.size() * this.scale) * pulse;
            Vec3 c = new Vec3(0.0, this.centerY, 0.0);
            // Only when the camera is almost inside the orb is it faded a little (at most 35%).
            Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
            double dist = camera.distanceTo(e.getPosition(delta).add(c));
            float dim = dist < radius * 0.6 ? (float)Math.min(0.41, (radius * 0.6 - dist) / (radius * 0.6) * 0.41) : 0.0f;
            // A soft outer halo makes the technique read clearly from afar.
            SlashShader.orb(pose, buffers, c, axes[0], axes[1], radius * 1.7f, phase(e, delta), this.kind, 0.6f);
            SlashShader.orb(pose, buffers, c, axes[0], axes[1], radius, phase(e, delta), this.kind, dim);
            SlashShader.orb(pose, buffers, c, axes[0], axes[1], radius * 0.45f, phase(e, delta), this.kind, dim);
        }
    }

    public static final class Ao extends Orb<AoEntity> {
        public Ao(EntityRendererProvider.Context ctx) { super(ctx, SlashShader.ORB_BLUE, 0.2f, 0.4f); }
    }

    public static final class Aka extends Orb<AkaEntity> {
        public Aka(EntityRendererProvider.Context ctx) { super(ctx, SlashShader.ORB_RED, 1.15f, 0.3f); }
    }

    public static final class Murasaki extends Orb<MurasakiEntity> {
        public Murasaki(EntityRendererProvider.Context ctx) { super(ctx, SlashShader.ORB_PURPLE, 1.0f, 0.5f); }
    }

    public static final class VoidShell extends PortEntityRenderer<VoidDomainEntity> {
        private static final int STREAKS = 320;

        public VoidShell(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.0f;
        }

        @Override
        protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(VoidDomainEntity e, float partialTicks) {
            return e.getBoundingBox().inflate(VoidDomainEntity.RADIUS);
        }

        @Override
        public boolean shouldRender(VoidDomainEntity e, net.minecraft.client.renderer.culling.Frustum culler, double x, double y, double z, float partialTicks) {
            return true;
        }

        private static double hash(int i, double salt) {
            double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
            return v - Math.floor(v);
        }

        @Override
        protected void render(VoidDomainEntity e, float yaw, float delta, PoseStack pose, PortBuffers buffers, int light) {
            float age = e.age() + delta;
            float phase = (age % 80.0f) / 80.0f;
            Vec3 c = new Vec3(0.0, 0.5, 0.0);
            float full = (float)VoidDomainEntity.RADIUS;
            int closeAt = e.closeAt();
            float radius, visibility, shatter, open;
            if (closeAt >= 0) {
                // Shatter: panels drift outward, fall and fade; the sky and the black hole go with them.
                shatter = Math.min(1.0f, (age - closeAt) / VoidDomainEntity.CLOSE_TICKS);
                radius = full;
                visibility = 1.0f - shatter;
                open = 1.0f;
            } else {
                open = (float)VoidDomainEntity.openFraction(age);
                radius = full * open;
                visibility = Math.min(1.0f, open * 1.4f);
                shatter = 0.0f;
            }
            if (radius < 0.3f) return;
            Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position().subtract(e.getPosition(delta));
            boolean inside = camera.distanceTo(c) < radius;
            SlashShader.Clip clip = clashClip(e, c, radius, delta);
            SlashShader.voidShell(pose, buffers, c, radius, inside, visibility, shatter, clip);
            Vec3[] axes = billboard();
            if (closeAt < 0 && open < 0.999f) {
                // Expanding front: a pale rim racing outward along the ground while the sphere opens.
                int n = 64;
                for (int i = 0; i < n; ++i) {
                    double a = i * Math.PI * 2.0 / n;
                    Vec3 q = c.add(Math.cos(a) * radius, -0.4, Math.sin(a) * radius);
                    if (clip != null && !clip.keeps(q)) continue;
                    SlashShader.orb(pose, buffers, q, axes[0], axes[1], 0.9f * (1.0f - open) + 0.25f, phase, SlashShader.ORB_BLUE);
                }
            }
            if (!inside) return;
            // A vast black hole opens in the sky behind the caster (the way they faced when casting).
            Vec3 behind = Vec3.directionFromRotation(0.0f, e.getYRot()).scale(-1.0);
            Vec3 hole = c.add(behind.scale(radius * 0.55)).add(0.0, radius * 0.28, 0.0);
            SlashShader.blackHole(pose, buffers, hole, axes[0], axes[1], radius * 0.36f, visibility, (age % 200.0f) / 200.0f, clip);
            this.stars(pose, buffers, c, radius, age, visibility, clip);
        }

        /** Seam against an overlapping Malevolent Shrine (radical plane of the two spheres), or null. */
        private static SlashShader.Clip clashClip(VoidDomainEntity e, Vec3 c, float radius, float delta) {
            SlashShader.Clip best = null;
            double bestDist = Double.MAX_VALUE;
            double shrineFull = cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity.RADIUS;
            for (cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity shrine : e.level().getEntitiesOfClass(
                    cn.blockforge.ryomensukuna.m2a542fea.entity.ShrineEntity.class, e.getBoundingBox().inflate(radius + shrineFull + 4.0), s -> !s.isRemoved())) {
                float x = Math.max(0.0f, Math.min(1.0f, (shrine.getVisualLife(delta) - 35.0f) / 65.0f));
                double b = shrineFull * x * x * (3.0f - 2.0f * x);
                if (b < 0.3) continue;
                Vec3 s = shrine.position().add(0.0, 0.035, 0.0).subtract(e.getPosition(delta)).subtract(c);
                double dist = s.length();
                if (dist < 1.0E-3 || dist >= radius + b || dist >= bestDist) continue;
                bestDist = dist;
                best = new SlashShader.Clip(c, s.scale(1.0 / dist), (dist * dist + radius * radius - b * b) / (2.0 * dist));
            }
            return best;
        }

        /** Stars rushing past: white and magenta streaks fly out from the centre and loop, like travel through space. */
        private void stars(PoseStack pose, PortBuffers buffers, Vec3 c, float radius, float age, float visibility, SlashShader.Clip clip) {
            if (visibility <= 0.01f) return;
            Camera cam = Minecraft.getInstance().gameRenderer.mainCamera();
            Vec3 toCamera = Vec3.directionFromRotation(cam.xRot(), cam.yRot()).scale(-1.0);
            float scale = radius / 29.0f;
            for (int i = 0; i < STREAKS; ++i) {
                double u = hash(i, 1.0) * 2.0 - 1.0;
                double theta = hash(i, 2.0) * Math.PI * 2.0;
                double ring = Math.sqrt(1.0 - u * u);
                Vec3 dir = new Vec3(ring * Math.cos(theta), u, ring * Math.sin(theta));
                double period = 12.0 + hash(i, 3.0) * 14.0;
                double t = (age / period + hash(i, 4.0)) % 1.0;
                double r = radius * (0.04 + 0.93 * t);
                double length = (1.2 + 4.5 * t) * scale;
                Vec3 head = c.add(dir.scale(r));
                if (clip != null && !clip.keeps(head)) continue;
                Vec3 tail = c.add(dir.scale(Math.max(0.0, r - length)));
                float fade = (float)Math.sin(Math.PI * t);
                float width = (0.05f + 0.06f * (float)hash(i, 5.0) + 0.04f * (float)t) * scale;
                SlashShader.streak(pose, buffers, tail, head, toCamera, width, fade * visibility, (float)hash(i, 6.0));
            }
        }
    }
}

package cn.blockforge.ryomensukuna.m2a542fea.client.render;

import cn.blockforge.ryomensukuna.m2a542fea.SukunaMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import cn.blockforge.ryomensukuna.m2a542fea.client.render.PortBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SlashShader {
    private static final net.minecraft.client.renderer.rendertype.RenderType LAYER = createLayer();
    private static net.minecraft.client.renderer.rendertype.RenderType createLayer() {
        var pipeline = net.minecraft.client.renderer.RenderPipelines.register(
            com.mojang.renderpearl.api.pipeline.RenderPipeline.builder()
                .withLocation(SukunaMod.id("pipeline/rift"))
                .withVertexShader(SukunaMod.id("core/rift"))
                .withFragmentShader(SukunaMod.id("core/rift"))
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS)
                .withCull(false)
                .withDepthStencilState(new com.mojang.renderpearl.api.pipeline.DepthStencilState(com.mojang.renderpearl.api.pipeline.CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(java.util.Optional.of(com.mojang.renderpearl.api.pipeline.BlendFunction.TRANSLUCENT), com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, 15))
                .build());
        return net.minecraft.client.renderer.rendertype.RenderType.create("sukuna_rift", net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup());
    }
    public static void init() { }

    public static Vec3 right(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }

    public static Vec3 up(float yaw, float pitch) {
        return Vec3.directionFromRotation((float)pitch, (float)yaw).cross(SlashShader.right(yaw)).normalize();
    }

    public static void blade(PoseStack matrices, PortBuffers consumers, Vec3 right, Vec3 up, Vec3 center, double angle, float halfLength, float halfWidth, float progress, int mode, float seed) {
        blade(matrices, consumers, right, up, center, angle, halfLength, halfWidth, progress, mode, seed, 1.0f);
    }

    /** {@code tint} below 0.75 selects the red-black Black Flash variant of slash (0-3) and ring (7) modes. */
    public static void blade(PoseStack matrices, PortBuffers consumers, Vec3 right, Vec3 up, Vec3 center, double angle, float halfLength, float halfWidth, float progress, int mode, float seed, float tint) {
        if (progress >= 1.0f) {
            return;
        }
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        Vec3 a = right.scale(c).add(up.scale(s)).scale((double)halfLength);
        Vec3 b = right.scale(-s).add(up.scale(c)).scale((double)halfWidth);
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Matrix4f matrix = matrices.last().pose();
        SlashShader.vertex(buffer, matrix, center.subtract(a).subtract(b), 0.0f, 0.0f, progress, mode, seed, tint);
        SlashShader.vertex(buffer, matrix, center.add(a).subtract(b), 1.0f, 0.0f, progress, mode, seed, tint);
        SlashShader.vertex(buffer, matrix, center.add(a).add(b), 1.0f, 1.0f, progress, mode, seed, tint);
        SlashShader.vertex(buffer, matrix, center.subtract(a).add(b), 0.0f, 1.0f, progress, mode, seed, tint);
    }

    /** Green channel that marks Gojo quads for the shader (a slash blade in mode 8 has exactly 1.0). */
    private static final float GOJO_TAG = 250.0f / 255.0f;
    public static final float ORB_BLUE = 0.1f;
    public static final float STAR_STREAK = 0.2f;
    public static final float BLACK_HOLE = 0.35f;
    public static final float ORB_RED = 0.45f;
    public static final float ORB_PURPLE = 0.7f;
    public static final float VOID_SHELL = 1.0f;

    /**
     * Where two domains meet, each keeps its own side of the seam (the radical plane of the two
     * spheres). Points with {@code (x - origin) . normal <= offset} belong to Unlimited Void.
     */
    public record Clip(Vec3 origin, Vec3 normal, double offset) {
        public double side(Vec3 x) {
            return x.subtract(this.origin).dot(this.normal) - this.offset;
        }

        public boolean keeps(Vec3 x) {
            return this.side(x) <= 0.0;
        }
    }

    private record Corner(Vec3 pos, float u, float v) {}

    /** Camera-facing glowing orb (Ao / Aka / Murasaki). */
    public static void orb(PoseStack matrices, PortBuffers consumers, Vec3 center, Vec3 right, Vec3 up, float radius, float phase, float kind) {
        orb(matrices, consumers, center, right, up, radius, phase, kind, 0.0f);
    }

    /** {@code dim} 0..1 fades the orb (used when it would fill the viewer's screen). */
    public static void orb(PoseStack matrices, PortBuffers consumers, Vec3 center, Vec3 right, Vec3 up, float radius, float phase, float kind, float dim) {
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Matrix4f matrix = matrices.last().pose();
        Vec3 a = right.scale(radius);
        Vec3 b = up.scale(radius);
        float d = Math.max(0.0f, Math.min(1.0f, dim));
        orbVertex(buffer, matrix, center.subtract(a).subtract(b), 0.0f, 0.0f, phase, kind, d);
        orbVertex(buffer, matrix, center.add(a).subtract(b), 1.0f, 0.0f, phase, kind, d);
        orbVertex(buffer, matrix, center.add(a).add(b), 1.0f, 1.0f, phase, kind, d);
        orbVertex(buffer, matrix, center.subtract(a).add(b), 0.0f, 1.0f, phase, kind, d);
    }

    /**
     * Unlimited Void shell as a latitude/longitude sphere, visible from both sides. From outside it
     * is a black sphere; from {@code inside} it shows deep space (uv carry longitude/latitude over the
     * whole sphere so the sky is continuous). {@code visibility} fades it; {@code shatter} (0..1)
     * pushes each panel outward and apart. With a {@code clip}, only the Void's side is drawn.
     */
    public static void voidShell(PoseStack matrices, PortBuffers consumers, Vec3 center, float radius, boolean inside, float visibility, float shatter, Clip clip) {
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Matrix4f matrix = matrices.last().pose();
        int lat = 24, lon = 48;
        float kind = 0.8f + 0.2f * Math.max(0.0f, Math.min(1.0f, visibility));
        float mode = inside ? 1.0f : 0.0f;
        for (int i = 0; i < lat; ++i) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            float v0 = (float)i / lat, v1 = (float)(i + 1) / lat;
            for (int j = 0; j < lon; ++j) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                float u0 = (float)j / lon, u1 = (float)(j + 1) / lon;
                float seed = ((i * lon + j) * 37 % 101) / 101.0f;
                float r = radius;
                Vec3 push = Vec3.ZERO;
                if (shatter > 0.0f) {
                    double tm = (t0 + t1) * 0.5, pm = (p0 + p1) * 0.5;
                    Vec3 normal = sphere(Vec3.ZERO, 1.0f, tm, pm);
                    push = normal.scale(shatter * (2.0 + seed * 6.0)).add(0.0, -shatter * shatter * (1.0 + seed * 3.0), 0.0);
                    r = radius * (1.0f - 0.15f * shatter * seed);
                }
                Corner[] quad = {
                    new Corner(sphere(center, r, t0, p0).add(push), u0, v0),
                    new Corner(sphere(center, r, t0, p1).add(push), u1, v0),
                    new Corner(sphere(center, r, t1, p1).add(push), u1, v1),
                    new Corner(sphere(center, r, t1, p0).add(push), u0, v1)};
                emit(buffer, matrix, clip == null ? quad : clip(quad, clip), mode, kind, seed);
            }
        }
    }

    /**
     * The black hole of Unlimited Void seen face-on: black horizon, a swirling white-blue vortex and
     * a thin bright ring. Camera-facing; {@code phase} 0..1 loops the swirl.
     */
    public static void blackHole(PoseStack matrices, PortBuffers consumers, Vec3 center, Vec3 right, Vec3 up, float radius, float visibility, float phase, Clip clip) {
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Matrix4f matrix = matrices.last().pose();
        Vec3 a = right.scale(radius);
        Vec3 b = up.scale(radius);
        Corner[] quad = {
            new Corner(center.subtract(a).subtract(b), 0.0f, 0.0f),
            new Corner(center.add(a).subtract(b), 1.0f, 0.0f),
            new Corner(center.add(a).add(b), 1.0f, 1.0f),
            new Corner(center.subtract(a).add(b), 0.0f, 1.0f)};
        float vis = Math.max(0.0f, Math.min(1.0f, visibility));
        emit(buffer, matrix, clip == null ? quad : clip(quad, clip), vis, BLACK_HOLE, phase);
    }

    /** Sutherland-Hodgman against the seam plane: the Void keeps the side where {@code side <= 0}. */
    private static Corner[] clip(Corner[] poly, Clip clip) {
        java.util.List<Corner> out = new java.util.ArrayList<>(6);
        for (int i = 0; i < poly.length; ++i) {
            Corner cur = poly[i], next = poly[(i + 1) % poly.length];
            double dc = clip.side(cur.pos()), dn = clip.side(next.pos());
            if (dc <= 0.0) out.add(cur);
            if (dc <= 0.0 != dn <= 0.0) {
                double t = dc / (dc - dn);
                out.add(new Corner(cur.pos().lerp(next.pos(), t), (float)(cur.u() + (next.u() - cur.u()) * t), (float)(cur.v() + (next.v() - cur.v()) * t)));
            }
        }
        return out.toArray(Corner[]::new);
    }

    /** A convex polygon as a fan of (possibly degenerate) quads for the QUADS pipeline. */
    private static void emit(VertexConsumer buffer, Matrix4f matrix, Corner[] poly, float phase, float kind, float seed) {
        if (poly.length < 3) return;
        if (poly.length == 4) {
            for (Corner c : poly) orbVertex(buffer, matrix, c.pos(), c.u(), c.v(), phase, kind, seed);
            return;
        }
        for (int i = 1; i + 1 < poly.length; ++i) {
            Corner[] tri = {poly[0], poly[i], poly[i + 1], poly[i + 1]};
            for (Corner c : tri) orbVertex(buffer, matrix, c.pos(), c.u(), c.v(), phase, kind, seed);
        }
    }

    /**
     * A star streak from {@code tail} to {@code head}, facing the camera ({@code toCamera} is the
     * direction from the streak to the viewer). {@code brightness} 0..1 fades it; {@code tint} 0..1
     * picks white or magenta.
     */
    public static void streak(PoseStack matrices, PortBuffers consumers, Vec3 tail, Vec3 head, Vec3 toCamera, float width, float brightness, float tint) {
        Vec3 along = head.subtract(tail);
        if (along.lengthSqr() < 1.0E-6) return;
        Vec3 side = along.cross(toCamera);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width);
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Matrix4f matrix = matrices.last().pose();
        float b = Math.max(0.0f, Math.min(1.0f, brightness));
        orbVertex(buffer, matrix, tail.subtract(side), 0.0f, 0.0f, b, STAR_STREAK, tint);
        orbVertex(buffer, matrix, head.subtract(side), 1.0f, 0.0f, b, STAR_STREAK, tint);
        orbVertex(buffer, matrix, head.add(side), 1.0f, 1.0f, b, STAR_STREAK, tint);
        orbVertex(buffer, matrix, tail.add(side), 0.0f, 1.0f, b, STAR_STREAK, tint);
    }

    private static Vec3 sphere(Vec3 c, float r, double theta, double phi) {
        return c.add(Math.sin(theta) * Math.cos(phi) * r, Math.cos(theta) * r, Math.sin(theta) * Math.sin(phi) * r);
    }

    private static void orbVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, float u, float v, float phase, float kind, float seed) {
        buffer.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setColor(Math.max(0.0f, Math.min(1.0f, phase)), GOJO_TAG, seed, kind).setUv(u, v);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, float u, float v, float progress, int mode, float seed, float tint) {
        buffer.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setColor(Math.max(0.0f, Math.min(1.0f, progress)), (float)mode / 8.0f, seed, tint).setUv(u, v);
    }

}
